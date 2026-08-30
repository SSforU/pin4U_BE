# 성능 측정 재현 절차

## 환경 전제

- Docker Desktop 실행 상태
- `docker compose up -d` 로 postgres:16, redis:7, 모니터링 스택 기동
- k6 설치 (`brew install k6`)
- Java 17, Gradle

## 1. 시드 데이터 적재

```bash
docker compose exec db psql -U pin4u -d pin4u_be -f /scripts/seed-perf-data.sql
```

시드 스크립트(`scripts/seed-perf-data.sql`)가 생성하는 데이터:

| 테이블 | 건수 | 비고 |
|--------|------|------|
| users | 5 | id 101~105 |
| stations | 10 | 서울 주요역 |
| requests | 50 | perf-req-001 ~ 050 |
| places | 500 | kakao:PERF-0001 ~ 0500 |
| request_place_aggregates | 500 | 요청당 10건 |

스크립트는 멱등(ON CONFLICT)이므로 재실행 가능.

## 2. 앱 기동

```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=pin4u_be DB_USER=pin4u DB_PASSWORD=pin4u \
REDIS_HOST=localhost REDIS_PORT=6379 \
KAKAO_REST_API_KEY=test OPENAI_API_KEY=test HMAC_SECRET=local-dev-only-secret-do-not-use-in-production!! \
./gradlew bootRun
```

앱은 8080 (API), 9091 (Actuator) 에서 리슨한다.

## 3. k6 부하 테스트

```bash
k6 run k6/theme1-query.js      # 조회 부하 (10 RPS, 30s)
k6 run k6/theme2-async.js       # 비동기 추천 (5 RPS, 30s)
k6 run k6/theme3-concurrency.js # 동시성 (50 VU × 1 iter)
```

결과 JSON은 `docs/perf/k6/`에 `handleSummary`로 자동 저장.

### 실측 결과 (2026-08-30, M-시리즈 Mac, Docker)

| 시나리오 | 요청 수 | checks | p95 응답시간 | 에러율 |
|----------|---------|--------|-------------|--------|
| theme1-query | 600 | 100% | 19.73 ms | 0% |
| theme2-async | 150 | 100% | 34.48 ms | 0% |
| theme3-concurrency | 50 | 100% | 66.23 ms | 0% |

## 4. EXPLAIN 측정

```bash
docker compose exec db psql -U pin4u -d pin4u_be
```

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM requests r
  JOIN stations s ON s.code = r.station_code
  JOIN request_place_aggregates rpa ON rpa.request_id = r.slug
  JOIN places p ON p.id = rpa.place_id
WHERE r.slug = 'perf-req-001'
ORDER BY rpa.recommended_count DESC;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM stations WHERE lower(name) LIKE '%강남%';
```

## 5. HikariCP pool sizing

pool size를 10, 30, 50으로 변경하며 k6 theme1을 실행.
각 설정의 TPS, p95, 에러율을 기록 → `docs/perf/connection-pool-sizing.md`

## 6. Bulk Insert 벤치마크

StationCsvImportRunner의 3가지 방식 비교:
- (A) 행별 find + save (원본)
- (B) saveAll + batch_size=100
- (C) JdbcTemplate.batchUpdate + ON CONFLICT

각 방식의 소요 시간 측정 → `docs/perf/bulk-insert-benchmark.md`
