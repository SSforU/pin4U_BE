# ADR-0005: 성능 측정 체계 및 실측 결과

- 상태: 수용
- 일시: 2026-08-30

## 맥락

포트폴리오에 기재하는 성능 수치는 반드시 실측에 기반해야 한다.
EXPLAIN 분석, connection pool sizing, bulk insert 비교 등
각 최적화 항목에 대해 before/after 수치를 확보하고
재현 가능한 형태로 기록할 필요가 있다.

## 결정

### EXPLAIN 분석
- 10K requests, 100K RPA 규모에서 V21 인덱스 효과 측정
- 핀 조회: 0.935ms → 0.200ms (79% 개선, Planning 최적화)
- 홈 대시보드: 2.288ms → 1.973ms (14% 개선, partial index)
- 역 검색: 10건 규모에서 Seq Scan이 최적이므로 차이 없음

### Connection Pool Sizing
- pool=10, 30, 50에서 동일 부하(10 RPS × 30s)로 비교
- pool=30이 p95 기준 최적 (13.48ms)
- pool=50은 유휴 커넥션 오버헤드로 오히려 성능 저하

### Bulk Insert
- 행별 find+save(600 쿼리) → JdbcTemplate batchUpdate(1 쿼리)
- 99.8% 쿼리 수 감소, ON CONFLICT로 멱등성 보장

## 결과

- 모든 실측 결과를 `docs/perf/` 하위에 개별 문서로 기록
- `PORTFOLIO-EVIDENCE.md`에 포트폴리오 기재용 요약 정리
- k6 JSON 원본이 `docs/perf/k6/`에 보존되어 제3자 검증 가능

## 원칙

- 수치를 지어내지 않는다 — 측정한 값만 기록한다
- 소규모 데이터에서 인덱스 효과가 없으면 "없다"고 기록한다
- 대규모 데이터를 별도로 적재하여 인덱스 효과를 확인한다
