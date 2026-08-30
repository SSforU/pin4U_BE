# 포트폴리오 성능 근거 (PORTFOLIO-EVIDENCE)

모든 수치는 2026-08-30 실측 결과이며, 측정 환경과 재현 절차는
[README.md](./README.md)에 기술되어 있다.

---

## 1. API 응답 성능

### 조회 API (theme1-query)
- **부하**: 10 RPS × 30초 (총 300 iteration, 600 요청)
- **p95 응답시간**: 19.73 ms
- **에러율**: 0%
- **근거**: [k6/theme1-query.json](./k6/theme1-query.json)

### 비동기 추천 쓰기 (theme2-async)
- **부하**: 5 RPS × 30초 (총 150 요청)
- **p95 응답시간**: 34.48 ms
- **에러율**: 0%
- **근거**: [k6/theme2-async.json](./k6/theme2-async.json)

### 동시성 처리 (theme3-concurrency)
- **부하**: 50 VU 동시 요청 (같은 리소스에 추천)
- **p95 응답시간**: 66.23 ms
- **에러율**: 0%
- **근거**: [k6/theme3-concurrency.json](./k6/theme3-concurrency.json)

## 2. 쿼리 최적화 (V21 인덱스)

### 핀 조회 (10K rows, 100K RPA)
- **인덱스 적용 전**: 0.935 ms (Planning 2.675ms)
- **인덱스 적용 후**: 0.200 ms (Planning 0.832ms)
- **개선율**: 79%
- **근거**: [explain-analysis.md](./explain-analysis.md)

### 홈 대시보드 (10K rows)
- **인덱스 적용 전**: 2.288 ms
- **인덱스 적용 후**: 1.973 ms
- **개선율**: 14%
- **근거**: [explain-analysis.md](./explain-analysis.md)

## 3. 벌크 INSERT 최적화

| 방식 | 쿼리 수 (N=300) | 라운드트립 |
|------|-----------------|-----------|
| 행별 find+save | 600 | 600 |
| JPA saveAll | 303 | 303 |
| **JdbcTemplate batchUpdate (현재)** | **1** | **1** |

- **개선율**: 쿼리 수 600 → 1 (99.8% 감소)
- **근거**: [bulk-insert-benchmark.md](./bulk-insert-benchmark.md)

## 4. Connection Pool 최적화

| pool size | p95 응답시간 |
|-----------|-------------|
| 10 | 19.61 ms |
| **30 (현재)** | **13.48 ms** |
| 50 | 18.22 ms |

- **최적 설정**: pool=30
- **근거**: [connection-pool-sizing.md](./connection-pool-sizing.md)

## 5. 동시성 제어

- 50 VU 동시 추천 시 100% 성공 (데이터 손실 없음)
- `UPDATE ... SET recommended_count = recommended_count + 1` 원자적 연산
- 통합 테스트 `ConnectionOccupancyTest`: pool=5에서 10 동시 요청, activeConnections ≤ 5 보장

---

## 측정 환경

- **하드웨어**: Apple Silicon Mac
- **DB**: postgres:16-alpine (Docker)
- **Redis**: redis:7-alpine (Docker)
- **Java**: 17
- **Spring Boot**: 3.5.4
- **k6**: Grafana k6

## 재현

```bash
docker compose up -d
docker compose exec db psql -U pin4u -d pin4u_be -f /scripts/seed-perf-data.sql
./gradlew bootRun  # local profile
k6 run k6/theme1-query.js
k6 run k6/theme2-async.js
k6 run k6/theme3-concurrency.js
```
