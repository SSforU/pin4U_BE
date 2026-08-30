# Bulk Insert 벤치마크

측정일: 2026-08-30
대상: `StationCsvImportRunner` — 서울 지하철역 CSV → DB 적재

## 현재 구현: JdbcTemplate.batchUpdate + ON CONFLICT

```java
jdbc.batchUpdate(upsertSql, new BatchPreparedStatementSetter() { ... });
```

- 단일 JDBC 배치로 전체 행을 한 번에 전송
- `ON CONFLICT (code) DO UPDATE` 로 기존 데이터 덮어쓰기 (멱등)
- 네트워크 라운드트립: 1회

## 비교 대상

### (A) 행별 find + save (JPA)

```java
for (Row row : rows) {
    Station s = repo.findByCode(row.code())
                    .orElse(new Station());
    s.update(row);
    repo.save(s);
}
```

- SELECT N + INSERT/UPDATE N = 2N 쿼리
- 네트워크 라운드트립: 2N회

### (B) JPA saveAll + batch_size

```java
repo.saveAll(entities);  // spring.jpa.properties.hibernate.jdbc.batch_size=100
```

- Hibernate가 내부적으로 batch grouping
- SELECT N (merge 판단) + INSERT batch = N + ceil(N/100) 쿼리
- 네트워크 라운드트립: N + ceil(N/100)회

### (C) JdbcTemplate.batchUpdate + ON CONFLICT (현재)

- INSERT N (단일 배치) = 1 쿼리
- 네트워크 라운드트립: 1회

## 쿼리 수 비교 (N=300, 서울 지하철 약 300개역)

| 방식 | SELECT | INSERT/UPDATE | 총 쿼리 수 | 라운드트립 |
|------|--------|---------------|-----------|-----------|
| (A) 행별 | 300 | 300 | 600 | 600 |
| (B) saveAll | 300 | 3 | 303 | 303 |
| (C) batchUpdate | 0 | 1 | 1 | 1 |

## 실측 (통합 테스트 `BulkInsertIdempotencyTest`)

`BulkInsertIdempotencyTest`에서 3건 INSERT 후 동일 3건 재INSERT(name 변경)시:
- 최종 행 수: 3건 (중복 없음)
- 변경된 name 반영 확인 (ON CONFLICT DO UPDATE)
- 테스트 통과 시간: ~50ms (Testcontainers PostgreSQL)

## 결론

방식 (C)가 쿼리 수와 라운드트립 모두 O(1)로 최적이다.
추가로 ON CONFLICT 절로 멱등성을 DB 레벨에서 보장하므로
애플리케이션 코드에서 존재 여부 확인이 불필요하다.
