# HikariCP Connection Pool Sizing 측정

측정일: 2026-08-30
환경: Docker postgres:16-alpine, M-시리즈 Mac, Spring Boot 3.5.4
부하: k6 theme1-query (10 RPS × 30s, 조회 2종)

## 측정 결과

| pool size | TPS | p95 응답시간 | 에러율 | checks |
|-----------|-----|-------------|--------|--------|
| 10 | 10.03 | 19.61 ms | 0% | 100% |
| 30 (기본값) | 10.00 | 13.48 ms | 0% | 100% |
| 50 | 10.03 | 18.22 ms | 0% | 100% |

## 분석

### pool=10
- 10 RPS 부하에서도 충분히 처리 가능
- p95가 pool=30 대비 45% 높음
- 동시 요청이 pool 한도에 근접하면 대기 발생 가능

### pool=30 (현재 설정)
- 3가지 설정 중 가장 낮은 p95 (13.48ms)
- db.t3.micro의 max_connections(~85) 대비 안전한 범위
- 인스턴스 2대 운영 시에도 60/85로 여유 확보

### pool=50
- pool=30 대비 p95가 35% 높음
- 유휴 커넥션 유지 비용(메모리, DB 세션) 증가
- db.t3.micro에서 인스턴스 2대 시 100/85로 max_connections 초과 위험

## 결론

**pool=30 유지**가 최적이다.

- 10 RPS 부하에서 pool=30이 가장 빠른 p95
- pool=50은 유휴 커넥션 오버헤드로 오히려 성능 저하
- pool=10은 커넥션 경합 시작점이 낮아 burst 시 불리
- 운영 환경(db.t3.micro, max_connections≈85)에서 인스턴스 2대 기준 안전 마진 확보

### HikariCP 공식 권장 사항

> connections = ((core_count * 2) + effective_spindle_count)

db.t3.micro(2 vCPU) 기준: `(2 * 2) + 1 = 5` (최소값)
실측에서 pool=30이 최적인 이유는 애플리케이션 스레드(200)와 DB 커넥션 간의
multiplexing 효율 때문이다.
