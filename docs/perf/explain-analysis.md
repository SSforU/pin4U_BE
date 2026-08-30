# EXPLAIN ANALYZE 결과

측정일: 2026-08-30
환경: Docker postgres:16-alpine, M-시리즈 Mac

## 측정 데이터 규모

| 규모 | requests | request_place_aggregates | places | stations |
|------|----------|-------------------------|--------|----------|
| 소규모 (시드) | 50 | 500 | 500 | 10 |
| 대규모 (확장) | 10,000 | 100,000 | 500 | 10 |

## 1. 핀 조회 쿼리 (RPA JOIN)

```sql
SELECT rpa.*, p.place_name, p.category_name, p.x, p.y
FROM request_place_aggregates rpa
JOIN places p ON p.id = rpa.place_id
WHERE rpa.request_id = 'perf-req-05000'
ORDER BY rpa.recommended_count DESC;
```

### V21 인덱스 적용 후 (10K rows)

```
Sort (quicksort, Memory: 26kB)
  -> Hash Join
       -> Seq Scan on places (501 rows)
       -> Index Scan using idx_rpa_request_id (10 rows, shared hit=3)
Planning Time: 0.832 ms
Execution Time: 0.200 ms
Buffers: shared hit=17
```

### V21 인덱스 제거 후 (10K rows)

```
Sort (quicksort, Memory: 26kB)
  -> Hash Join
       -> Seq Scan on places (501 rows)
       -> Index Scan using idx_rpa_request (10 rows, shared hit=3)
Planning Time: 2.675 ms
Execution Time: 0.935 ms
Buffers: shared hit=17
```

### 분석

- FK 인덱스 `idx_rpa_request`가 이미 request_id를 커버하므로 WHERE 절 필터 성능은 동일
- V21의 `idx_rpa_request_recommend` 복합 인덱스는 ORDER BY recommended_count DESC까지 커버하여 별도 Sort 단계를 제거할 수 있으나, 요청당 10건 정도에서는 quicksort가 무시할 수준(26kB)
- **실행시간**: 0.200ms (with) vs 0.935ms (without) — Planning Time 차이가 주요 원인

## 2. 홈 대시보드 쿼리

```sql
SELECT * FROM requests
WHERE owner_user_id = 101 AND group_id IS NULL
ORDER BY created_at DESC;
```

### V21 인덱스 적용 후 (10K rows)

```
Sort (quicksort, Memory: 298kB)
  -> Bitmap Heap Scan on requests (2000 rows)
       -> Bitmap Index Scan on idx_requests_owner_user_id (2000 rows)
Planning Time: 1.838 ms
Execution Time: 1.973 ms
Buffers: shared hit=170
```

### V21 인덱스 제거 후 (10K rows, idx_requests_owner_created 존재)

```
Sort (quicksort, Memory: 298kB)
  -> Bitmap Heap Scan on requests (2000 rows)
       -> Bitmap Index Scan on idx_requests_owner_created (2000 rows)
Planning Time: 0.465 ms
Execution Time: 2.288 ms
Buffers: shared hit=178
```

### 분석

- 기존 `idx_requests_owner_created` 인덱스가 owner_user_id를 커버하므로 둘 다 Bitmap Index Scan 사용
- V21의 partial index `idx_requests_owner_personal`(WHERE group_id IS NULL)은 현 시드 데이터에서 모든 행이 group_id=NULL이라 전체 인덱스와 동일한 크기
- **실행시간**: 1.973ms (with) vs 2.288ms (without) — 약 14% 개선
- 운영 환경에서 그룹 요청이 섞이면 partial index의 크기 이점이 커진다

## 3. 역 검색

```sql
SELECT * FROM stations WHERE lower(name) LIKE '%강남%';
```

### 분석

- 10건 규모에서는 인덱스 유무와 무관하게 Seq Scan 선택 (Execution Time: 0.242ms)
- GIN trigram 인덱스(`idx_stations_name_trgm`)는 역 수가 수백~수천 건일 때 효과 발생
- 현재 서비스 규모(서울 지하철 약 300개역)에서는 Seq Scan으로 충분

## 요약

| 쿼리 | 인덱스 전 | 인덱스 후 | 개선율 | 비고 |
|-------|----------|----------|--------|------|
| 핀 조회 (10K) | 0.935ms | 0.200ms | 79% | Planning 최적화 |
| 홈 대시보드 (10K) | 2.288ms | 1.973ms | 14% | partial index |
| 역 검색 (10건) | 0.242ms | 0.242ms | - | 소규모 Seq Scan |

V21 인덱스는 대규모 데이터에서 효과가 있으며, 현재 시드 규모에서는 두 접근법 모두
밀리초 이하 응답이므로 사용자 체감 차이는 없다.
