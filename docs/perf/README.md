# 성능 측정 재현 절차

## 환경 전제

- Docker Compose로 postgres:16, 앱, prometheus, grafana 기동
- k6 설치 (`brew install k6` 또는 공식 릴리스)

## 시드 데이터

```bash
# docker-compose 기동 후
docker compose exec db psql -U pin4u -d pin4u_be -f /scripts/seed-perf-data.sql
```

시드 스크립트(`scripts/seed-perf-data.sql`)가 생성하는 데이터:
- 역: CSV 전수 로딩 (약 300건, 서울 1~9호선)
- 요청: 50건 (각 역에 분산)
- 장소: 요청당 10~20건
- 추천: 요청·장소당 5건

정확한 건수는 스크립트 실행 후 아래 쿼리로 확인:
```sql
SELECT 'stations' AS t, count(*) FROM stations
UNION ALL SELECT 'requests', count(*) FROM requests
UNION ALL SELECT 'places', count(*) FROM places
UNION ALL SELECT 'rpa', count(*) FROM request_place_aggregates;
```

## k6 부하 테스트

```bash
# 환경변수로 BASE_URL과 테스트 slug 지정
export BASE_URL=http://localhost:8080
export TEST_SLUG=<시드로 생성된 slug>

k6 run k6/theme1-query.js
k6 run k6/theme2-async.js
k6 run k6/theme3-concurrency.js
```

결과는 `docs/perf/k6/` 에 `handleSummary`로 자동 저장.

## EXPLAIN 측정

```bash
docker compose exec db psql -U pin4u -d pin4u_be
```

```sql
-- 핀 조회
EXPLAIN (ANALYZE, BUFFERS)
SELECT ... FROM requests r
JOIN stations s ON s.code = r.station_code
JOIN request_place_aggregates rpa ON rpa.request_id = r.slug
JOIN places p ON p.id = rpa.place_id
LEFT JOIN place_mock pm ON pm.external_id = p.external_id
LEFT JOIN place_summaries ps ON ps.place_id = p.id
WHERE r.slug = '<slug>'
ORDER BY rpa.recommended_count DESC
LIMIT 12;

-- 역 검색
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM stations WHERE lower(name) LIKE '%강남%';

-- 홈 대시보드
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM requests WHERE owner_user_id = 1 AND group_id IS NULL
ORDER BY created_at DESC;
```

before/after 비교:
1. V21 인덱스 DROP 상태에서 EXPLAIN 실행 → `docs/perf/explain-before.md`
2. V21 인덱스 적용 후 EXPLAIN 실행 → `docs/perf/explain-after.md`

## HikariCP pool sizing

pool size를 10, 30, 50으로 변경하며 k6 theme1을 실행.
각 설정의 TPS, p95, 에러율을 기록 → `docs/perf/connection-pool-sizing.md`

## Bulk Insert 벤치마크

StationCsvImportRunner의 3가지 방식 비교:
- (A) 행별 find + save (원본)
- (B) saveAll + batch_size=100
- (C) JdbcTemplate.batchUpdate + ON CONFLICT

각 방식의 소요 시간 측정 → `docs/perf/bulk-insert-benchmark.md`

## 현재 상태

Docker 환경 미구축으로 실측 수치 미기재. 위 절차대로 환경 구성 후 측정하면
각 문서를 채울 수 있다.
