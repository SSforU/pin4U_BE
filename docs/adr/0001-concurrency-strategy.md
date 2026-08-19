# ADR-0001: 추천 수 동시성 전략 선택

## 상태
채택 (2026-08-20)

## 맥락
`request_place_aggregates.recommended_count` 갱신 시 동시 요청에 의한 갱신 유실(lost update) 방지가 필요하다.

## 비교 대상

### (A) 원자적 UPDATE
```sql
UPDATE request_place_aggregates
SET recommended_count = recommended_count + 1
WHERE request_id = ? AND place_id = ?
```
- DB가 행 레벨 락을 자동으로 관리하므로 애플리케이션 재시도 로직 불필요.
- 단일 SQL이므로 왕복 1회.
- 카운터 증감에 특화된 가장 단순한 구조.

### (B) 낙관적 락 (@Version + @Retryable)
- 엔티티에 `@Version` 컬럼 추가 → 충돌 시 `OptimisticLockingFailureException` → `@Retryable(maxAttempts=3)`.
- 복합 비즈니스 로직(여러 필드 동시 변경)에 적합하나, 단순 카운터 증감에는 과도한 구조.
- 충돌률이 높으면 재시도 비용 증가.

## 결정
**(A) 원자적 UPDATE를 채택한다.**

### 근거
1. `recommended_count` 갱신은 단일 필드 증감이므로 원자적 UPDATE가 정확히 적합하다.
2. 애플리케이션 레벨 재시도(@Retryable)가 불필요하므로 코드 복잡도가 낮다.
3. Postgres는 `UPDATE ... SET col = col + 1`에 대해 행 레벨 락을 자동 적용하므로
   동시 100건이 도착해도 직렬 처리되어 갱신 유실이 원천 차단된다.
4. `@Version` 컬럼은 이미 추가(V22)되어 있으므로, 향후 복합 비즈니스 로직이 필요해지면
   낙관적 락으로 전환할 수 있다. 현재는 `@Version`이 JPA dirty-checking 시
   WHERE 조건에 자동 포함되어 추가적인 안전장치로 작동한다.

## 실측 수치
Docker 환경 미구축으로 실측 불가. 동시성 테스트(ExecutorService + CountDownLatch)는
Testcontainers 통합 테스트로 작성되어 CI에서 실행 가능한 상태이나,
로컬에서는 Docker 미실행으로 수치를 기록하지 못했다.

향후 측정 시 이 문서에 다음을 추가할 것:
- 동시 100건 / 500건 시 최종 카운트 정합성
- 처리량(TPS)
- 재시도율(B 방식 한정)

## 결과
- `RequestPlaceAggregateRepository.atomicIncrementCount()` 사용
- `spring-retry` 의존성 및 `@EnableRetry` 제거 (불필요)
- `@Version` 컬럼은 향후 전환 가능성을 위해 유지
