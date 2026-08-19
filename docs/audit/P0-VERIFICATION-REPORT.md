# P0 검증 보고서
생성: 2026-08-19 / 대상 커밋: aa7a5a0629d6347a19752fc69aa145cde746f8b4

## 0. 총평 (5줄 이내)

전체 판정: **PARTIAL**

코드 구조적 리팩토링(인증 전환, 타임아웃, Resilience4j 배선, N+1 제거, 벌크 Insert 등)은 실제로 수행되었으나,
**테스트가 단위 테스트 8건(AuthTokenProviderTest)에 불과**하고, DoD가 요구한 통합 테스트·동시성 테스트·MockWebServer 테스트가 전무하다.
성능 측정 산출물(EXPLAIN, k6 결과, 벤치마크)이 저장소에 단 하나도 없어 수치 주장이 전부 UNVERIFIABLE이다.
`AiSummaryServiceImpl.generateAndSaveSummary`의 트랜잭션 경계 미분리, `GroupMapService`의 readOnly 위반(ensureMocks 호출) 등
핵심 설계 결함이 잔존한다.

가장 심각한 미해결 항목 3가지:
1. `AiSummaryServiceImpl:61` — `@Transactional` 안에서 20초 OpenAI blocking 호출 → 커넥션 풀 고갈 위험 그대로
2. 위조 쿠키 401/403 통합 테스트 부재 — 보안 이슈의 핵심 DoD 미충족
3. 성능 측정 산출물 0건 — 포트폴리오 수치 근거 전무

## 1. 이슈별 판정 요약

| 이슈 | 제목 | 판정 | DoD 충족 | 핵심 근거 |
|------|------|------|----------|-----------|
| 0 | 긴급 보안 | PARTIAL | 9/12 | HMAC 인증 전환 완료, 위조 쿠키 통합 테스트 부재, port 8080 0.0.0.0/0 잔존 |
| 1 | 저장소 위생 | PASS | 3/3 | 잡파일 전수 제거, flyway 환경변수화, .gitignore 정합성 확인 |
| 2 | 테스트 정상화 | PARTIAL | 5/7 | onlyIf 제거 완료, 실행 테스트 8건(단위만), JaCoCo 생성 확인, integration 테스트 Docker 의존 |
| 3 | HTTP 타임아웃 | PARTIAL | 4/5 | 3개 WebClient 빈 전부 타임아웃 적용, MockWebServer 타임아웃 테스트 부재 |
| 4 | Resilience4j | PARTIAL | 4/5 | 의존성+애노테이션+폴백+actuator 완료, CB 상태 전이 테스트 부재 |
| 5 | 조회 경로 분리 | PARTIAL | 4/6 | RequestDetailServiceImpl AI 호출 제거 완료, GroupMapService에 ensureMocks 잔존(readOnly 위반), 외부 호출 0건 테스트 부재 |
| 6 | 비동기 파이프라인 | PARTIAL | 4/9 | 이벤트 배선 완료, sleep 제거, AsyncConfig 보강, 그러나 트랜잭션 미분리/ai_summary_job 미생성/summary-status 엔드포인트 부재/E2E 테스트 부재 |
| 7 | Caffeine 캐시 | PARTIAL | 4/5 | @EnableCaching+CaffeineCacheManager+키 정규화 완료, 캐시 hit 테스트 부재 |
| 8 | 인덱스 재설계 | PARTIAL | 3/5 | V21 복합 인덱스 4개+pg_trgm 생성, CONCURRENTLY 미사용(주석만), EXPLAIN 산출물 부재 |
| 9 | HomeService N+1 | PARTIAL | 4/5 | 배치 집계 전환 완료, JDBC 폴백 제거, 쿼리 수 단언 테스트 부재 |
| 10+13 | 동시성 | PARTIAL | 3/9 | @Version+version 마이그레이션+Thread.sleep 제거, atomicIncrementCount 미사용(데드 코드), 동시성 테스트/ADR/ON CONFLICT 부재 |
| 11 | Bulk Insert | PARTIAL | 3/5 | batchUpdate+ON CONFLICT upsert 완료, 벤치마크 문서/멱등성 테스트 부재 |
| 12+14 | HikariCP+k6 | PARTIAL | 4/7 | 양쪽 yml hikari 설정 완료, k6 스크립트 수정 완료, 실행 결과/connection-pool-sizing 문서 부재 |
| 15 | 의존성 정리 | PARTIAL | 4/5 | aws/redis 제거+BOM 정리+dependabot 완료, spotless 미설정 |

## 2. 워크플로우 준수

| 항목 | 판정 | 상세 |
|------|------|------|
| 이슈 생성 (feat:/fix: 컨벤션) | PARTIAL | 15건 전부 생성, 7건만 배경/작업/완료조건 3섹션 구조 준수. 8건은 단일 문장 body |
| 브랜치명 `feat/#N` | PARTIAL | 13/15 준수. PR#56은 `feat/#46`(이슈번호 불일치, 실제 이슈 #55). PR#64는 `chore/remove-internal-doc` |
| PR `Closes #N` | PASS | 15건 전부 이슈를 닫음 |
| squash merge | PASS | 15건 전부 squash merge |
| main 직접 push | PASS (감사 기간) | P0 이전 17건 직접 push 존재하나, 감사 기간(#28~#64) 중에는 0건 |
| 누락 이슈 | PASS | 0~15 전부 착수됨 |

## 3. 이슈별 상세 검증

### [이슈 0] 긴급 보안 — PARTIAL (9/12)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| variables.tf 평문 비밀번호 제거 | PASS | `variables.tf:2` description에 실제 값 없음. 단, git 히스토리에 `Lsy12052781!` 잔존 |
| HMAC 서명 인증 전환 | PASS | `LoginUserArgumentResolver.java:67` → `tokenProvider::validateToken`. 평문 `Long.valueOf` 경로 없음 |
| 서명 키 외부 주입 | PASS | `AuthTokenProvider.java:26` `@Value("${app.auth.hmac-secret}")`, 최소 32자 검증 |
| @CookieValue 0건 | PASS | `grep -rn "@CookieValue" src/main/java` → 0건 |
| @LoginUser 전 컨트롤러 | PASS | 14개 사용처 (8개 컨트롤러), 쓰기 엔드포인트 전수 `required=true` |
| 시연용/생략 주석 제거 | PASS | 0건 |
| 쓰기 엔드포인트 인증 | PASS | POST/DELETE/PATCH 전수 조사, login/logout 외 전부 @LoginUser(required=true) |
| 0.0.0.0/0 축소 | **FAIL** | `main.tf:61` port 8080 여전히 0.0.0.0/0. SSH/Prometheus/Grafana는 제거됨. 8080은 앱 포트로 ALB 없는 구조에서는 불가피할 수 있으나 명시적 판단 근거 없음 |
| ECR Principal 한정 | PASS | `main.tf:262-270` `compact([ec2_role.arn, var.ci_iam_arn])` |
| show-details | PASS | `application.yml:118` `when-authorized` |
| AuthTokenProviderTest | PASS | 8건: 위조uid/서명 거부, 평문 거부, 쓰레기 거부, 다른 시크릿 거부 |
| **위조 쿠키 통합 테스트** | **FAIL** | MockMvc로 위조 쿠키 → 401 단언하는 테스트 없음 |

### [이슈 1] 저장소 위생 — PASS (3/3)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| 잡파일 추적 해제 | PASS | `git ls-files` grep 결과 0건 |
| flyway 평문 비밀번호 | PASS | `build.gradle:12` `findProperty("flyway.password") ?: ""` |
| .gitignore 정합성 | PASS | application-prod.yml 추적 유지, .gitignore에서 해당 라인 제거 |

### [이슈 2] 테스트 정상화 — PARTIAL (5/7)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| onlyIf 제거 | PASS | `build.gradle` grep 0건 |
| 테스트 실제 실행 | PASS | `./gradlew clean build` → test task 실행, 8건 통과 |
| @ServiceConnection | PASS | `TestcontainersConfiguration.java:13` |
| ci.yml PR 트리거 | PASS | `.github/workflows/ci.yml:4` `pull_request` |
| deploy.yml v4 갱신 | PASS | checkout@v4, setup-java@v4, configure-aws-credentials@v4, ecr-login@v2 |
| **FlywayMigrationTest 실행 확인** | **PARTIAL** | `@Tag("integration")` 적용되어 기본 test task에서 제외. CI에서만 실행. 로컬 검증 불가 |
| **JaCoCo 커버리지** | PARTIAL | 리포트 생성 확인. 단, 커버리지 대상이 AuthTokenProvider 1개 클래스뿐이므로 전체 커버리지는 극히 낮음 |

### [이슈 3] HTTP 타임아웃 — PARTIAL (4/5)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| CONNECT_TIMEOUT_MILLIS 설정 | PASS | `WebClientConfig.java:72` |
| 3개 WebClient 빈 전수 타임아웃 | PASS | kakaoWebClient(3s), kakaoOAuthWebClient(3s), openaiWebClient(20s) |
| KakaoOAuthClient 빈 주입 | PASS | `KakaoOAuthClient.java:14` `private final WebClient kakaoOAuthWebClient` |
| onErrorReturn(null) 0건 | PASS | grep 결과 0건 |
| **타임아웃 테스트** | **FAIL** | MockWebServer 지연 주입 테스트 없음 |

### [이슈 4] Resilience4j — PARTIAL (4/5)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| 의존성 존재 | PASS | `build.gradle:48-49` resilience4j-spring-boot3:2.2.0 |
| 3곳 애노테이션 배선 | PASS | KakaoSearchAdapterImpl(CB+Retry), AiKeywordServiceImpl(CB+Retry), AiSummaryServiceImpl(Bulkhead+CB+Retry) |
| fallback 메서드 | PASS | 3곳 모두 Throwable 파라미터, 로그 출력, 정상 폴백 반환 |
| actuator 노출 | PASS | `application.yml:115` circuitbreakers, circuitbreakerevents 포함 |
| **CB 상태 전이 테스트** | **FAIL** | CircuitBreakerRegistry 기반 OPEN 전이 테스트 없음 |

### [이슈 5] 조회 경로 분리 — PARTIAL (4/6)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| RequestDetailServiceImpl AI 호출 제거 | PASS | `RequestDetailServiceImpl.java:49-106` WebClient/aiSummaryService 호출 없음, 쿼리 결과에서 ai_summary_text 직접 읽음 |
| readOnly=true | PASS | `RequestDetailServiceImpl.java:25` 클래스 레벨 readOnly, 메서드 오버라이드 없음 |
| NULL::TEXT 제거 + place_summaries JOIN | PASS | `RequestDetailQueryRepository.java:65-67` `LEFT JOIN place_summaries ps ON ps.place_id = p.id` |
| **GroupMapService ensureMocks 잔존** | **FAIL** | `GroupMapService.java:112` `mockAllocator.ensureMocks(need)` — readOnly 트랜잭션 안에서 쓰기 호출. 런타임 에러 또는 dirty write 위험 |
| GroupMapService readOnly | PARTIAL | `GroupMapService.java:37` `@Transactional(readOnly=true)` 선언은 했으나 ensureMocks가 쓰기를 수행 |
| **외부 호출 0건 테스트** | **FAIL** | MockWebServer 미도달 테스트 없음 |

### [이슈 6] 비동기 파이프라인 — PARTIAL (4/9)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| 이벤트 HTTP 도달 경로 | PASS | `RequestController.create()` → `RequestServiceImpl.create():102` → `eventPublisher.publishEvent(new RequestCreatedEvent(...))` → `RequestEventListener.handleRequestCreated()` `@Async @TransactionalEventListener(AFTER_COMMIT)` |
| Thread.sleep 0건 | PASS | grep 0건 |
| AsyncConfig 보강 | PASS | CallerRunsPolicy, waitForTasksToCompleteOnShutdown(true), awaitTerminationSeconds(30), AsyncUncaughtExceptionHandler |
| server.shutdown: graceful | PASS | `application.yml:76` |
| **트랜잭션 경계 미분리** | **FAIL** | `AiSummaryServiceImpl.java:61` `@Transactional`으로 generateAndSaveSummary 전체를 감쌈. 내부 generateSummary()의 OpenAI blocking 호출(최대 20초)이 트랜잭션+DB 커넥션 점유 상태에서 실행됨. [조회tx → tx밖 외부호출 → 저장tx] 3단 분리가 이루어지지 않음 |
| **ai_summary_job 테이블** | **FAIL** | V1~V22 마이그레이션 전수 확인, ai_summary_job 테이블 없음 |
| **summary-status 엔드포인트** | **FAIL** | 컨트롤러 전수 grep, `/summary-status` 경로 없음 |
| **E2E 통합 테스트** | **FAIL** | POST → 비동기 완료 → 조회 반영 검증 테스트 없음 |
| **실패 시 status=FAILED 기록** | **FAIL** | ai_summary_job 자체가 없으므로 불가 |

### [이슈 7] Caffeine 캐시 — PARTIAL (4/5)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| @EnableCaching | PASS | `CacheConfig.java:13` |
| CaffeineCacheManager | PASS | `CacheConfig.java:17-25` maximumSize(1000), expireAfterWrite(1h), recordStats() |
| spring.cache.type: caffeine | PASS | `application.yml:5` |
| 캐시 키 정규화 | PASS | `StationServiceImpl.java:31-33` `normalizeKey(#q)` → trim().toLowerCase() |
| **캐시 hit 테스트** | **FAIL** | "2회 호출 시 DB 쿼리 1회" 테스트 없음 |

### [이슈 8] 인덱스 재설계 — PARTIAL (3/5)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| V20 중복 DROP | PASS | `V21:6-7` idx_requests_slug, idx_groups_slug DROP |
| 복합 인덱스 생성 | PASS | 4개: rpa(request_id, recommended_count DESC), requests(owner_user_id, created_at DESC), requests(group_id, created_at DESC), stations gin(lower(name)) |
| pg_trgm | PASS | `V21:25` CREATE EXTENSION IF NOT EXISTS pg_trgm |
| **CONCURRENTLY 미사용** | **FAIL** | 주석으로 "운영 환경에서는 CONCURRENTLY 권장"이라 썼을 뿐, 실제 SQL은 일반 CREATE INDEX. Flyway 트랜잭션 비활성 처리도 없음 |
| **EXPLAIN 산출물** | **FAIL** | `docs/perf/explain-before.md`, `explain-after.md` 없음 |

### [이슈 9] HomeService N+1 — PARTIAL (4/5)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| 루프 내 개별 쿼리 제거 | PASS | `HomeService.java:49` sumByRequestIds 배치 호출, 루프 내 sumByRequestId 0건 |
| 배치 메서드 사용 | PASS | `HomeService.java:49` rpaRepo.sumByRequestIds(slugs) |
| JDBC 폴백 제거 | PASS | JdbcTemplate import/사용 없음 |
| default_batch_fetch_size 주석 처리 | PASS | `application.yml:13` 주석 + 무효 사유 명시 |
| **쿼리 수 단언 테스트** | **FAIL** | Hibernate statistics 기반 테스트 없음 |

### [이슈 10+13] 동시성 — PARTIAL (3/9)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| @Version | PASS | `RequestPlaceAggregate.java:29` |
| version 마이그레이션 | PASS | `V22__add_rpa_version_column.sql` |
| spring-retry 의존성 | PASS | `build.gradle:45`, `RetryConfig.java:7` @EnableRetry. 단 @Retryable 사용처 0건 — 데드 코드 |
| Thread.sleep 0건 | PASS | grep 0건 |
| **atomicIncrementCount 미사용** | **FAIL** | `RequestPlaceAggregateRepository.java:42-46` 메서드 존재하나 호출하는 코드 없음 — 데드 코드 |
| **동시성 테스트** | **FAIL** | ExecutorService/CountDownLatch 테스트 없음 |
| **ON CONFLICT 처리** | **FAIL** | 추천 저장 경로에 ON CONFLICT 없음. UNIQUE 위반 시 DataIntegrityViolationException이 컨트롤러에서 500으로 반환됨 |
| **ADR 문서** | **FAIL** | `docs/adr/` 디렉토리 없음 |
| **중복 추천 UNIQUE 제약** | PASS | `V16:55` ux_req_place (request_id, place_id) — 단, 이는 기존 마이그레이션이지 P0에서 추가한 것 아님 |

### [이슈 11] Bulk Insert — PARTIAL (3/5)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| batchUpdate 사용 | PASS | `StationCsvImportRunner.java:78` |
| 행별 find+save 제거 | PASS | 단일 배치 upsert로 전환 |
| ON CONFLICT upsert | PASS | `StationCsvImportRunner.java:69-75` |
| **벤치마크 문서** | **FAIL** | `docs/perf/bulk-insert-benchmark.md` 없음 |
| **멱등성 테스트** | **FAIL** | 재실행 중복 방지 테스트 없음 |

### [이슈 12+14] HikariCP + k6 — PARTIAL (4/7)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| 양쪽 yml hikari 설정 | PASS | application.yml:15-21, application-prod.yml:7-13 |
| 근거 주석 | PARTIAL | application.yml에만 존재, prod에는 없음 |
| k6 URL/check/handleSummary/threshold | PASS | 3개 스크립트 전부 수정 확인 |
| **k6 결과 파일** | **FAIL** | `docs/perf/k6/` 에 .gitkeep만 존재 |
| **connection-pool-sizing 문서** | **FAIL** | 없음 |
| **pool size 10/30/50 실측표** | **FAIL** | 없음 |
| **재현 절차 문서** | **FAIL** | 없음 |

### [이슈 15] 의존성 정리 — PARTIAL (4/5)

| DoD 항목 | 판정 | 근거 |
|----------|------|------|
| spring-cloud-starter-aws 제거 | PASS | build.gradle에 없음 |
| spring-boot-starter-data-redis 제거 | PASS | build.gradle에 없음 |
| BOM 버전 중복 제거 | PASS | 남은 명시 버전은 BOM 비관리(springdoc, resilience4j)뿐 |
| dependabot.yml | PASS | `.github/dependabot.yml` 존재 |
| **spotless** | **FAIL** | build.gradle에 spotless 플러그인 없음 |

## 4. 회귀 및 무결성

### 4.1 클린 빌드
`./gradlew clean build` — **BUILD SUCCESSFUL** (4s). 경고 없음. 테스트 8건 실행.

### 4.2 마이그레이션 무결성
**주의**: `git log -p --all -- src/main/resources/db/migration/` 확인 결과, **V15__rpa_request_id_to_varchar.sql이 이전 커밋에서 실질적으로 재작성**된 이력이 있음 (5줄 → 52줄). 이는 P0 이전에 발생한 것이지만, 이미 적용된 DB에서 Flyway 체크섬 불일치를 유발할 수 있다. P0 작업에서 기존 마이그레이션을 수정한 것은 아님.

### 4.3 새로 생긴 문제

| 문제 | 위치 | 심각도 |
|------|------|--------|
| GroupMapService readOnly 위반 | `GroupMapService.java:112` ensureMocks는 쓰기인데 readOnly=true 트랜잭션 | HIGH |
| atomicIncrementCount 데드 코드 | `RequestPlaceAggregateRepository.java:42-46` 호출처 0건 | LOW |
| @Retryable 미사용 | spring-retry 의존성 + @EnableRetry 있으나 @Retryable 0건 | LOW |
| AiSummaryServiceImpl 트랜잭션 경계 | `:61` @Transactional 안에서 20초 blocking 호출 | CRITICAL |

## 5. 수치 정직성 감사

| 문서 위치 | 주장 수치 | 원본 산출물 | 대조 결과 |
|-----------|-----------|------------|-----------|
| application.yml:15 주석 "max_connections ≈ 85" | db.t3.micro 85개 | 없음 | **UNVERIFIABLE** |
| application.yml:21 주석 "29분" | RDS wait_timeout=30분 | 없음 | **UNVERIFIABLE** |
| docs/perf/explain-before.md | (파일 부재) | — | **FABRICATED** (약속만 하고 미생성) |
| docs/perf/explain-after.md | (파일 부재) | — | **FABRICATED** |
| docs/perf/bulk-insert-benchmark.md | (파일 부재) | — | **FABRICATED** |
| docs/perf/connection-pool-sizing.md | (파일 부재) | — | **FABRICATED** |
| docs/adr/0001-concurrency-strategy.md | (파일 부재) | — | **FABRICATED** |
| docs/perf/k6/*.json | (파일 부재) | — | **FABRICATED** |

**참고**: "FABRICATED"는 DoD에서 약속한 산출물이 생성되지 않았다는 의미이며, 거짓 수치를 날조했다는 뜻은 아니다. 수치 자체가 존재하지 않는다.

## 6. 새로 발견된 문제

| 번호 | 문제 | 대상 파일 | 심각도 |
|------|------|-----------|--------|
| N1 | GroupMapService.readOnly 트랜잭션에서 MockAllocator.ensureMocks(쓰기) 호출 | `GroupMapService.java:112` | HIGH |
| N2 | AiSummaryServiceImpl @Transactional 안에서 최대 20초 blocking WebClient 호출 — 커넥션 풀 고갈 | `AiSummaryServiceImpl.java:61,143` | CRITICAL |
| N3 | atomicIncrementCount 메서드가 존재하나 호출처 없음 — 데드 코드 | `RequestPlaceAggregateRepository.java:42-46` | LOW |
| N4 | spring-retry/@EnableRetry 설정 있으나 @Retryable 사용처 0건 — 데드 코드 | `RetryConfig.java`, `build.gradle:45` | LOW |
| N5 | main.tf:61 port 8080이 0.0.0.0/0으로 개방 (ALB 없는 직접 노출) | `main.tf:61` | MEDIUM |

## 7. 잔여 작업 목록 (우선순위 순)

| 우선순위 | 항목 | 대상 파일 | 필요 사유 |
|----------|------|-----------|-----------|
| P0 | AiSummaryServiceImpl 트랜잭션 3단 분리 | `AiSummaryServiceImpl.java:61` | 20초 blocking 중 DB 커넥션 점유 → 풀 고갈 |
| P0 | GroupMapService ensureMocks 제거 또는 별도 트랜잭션 분리 | `GroupMapService.java:112` | readOnly 위반, 런타임 오류 가능 |
| P0 | 위조 쿠키 → 401/403 통합 테스트 | 신규 | 보안 DoD 핵심 미충족 |
| P1 | 동시성 테스트 (ExecutorService + CountDownLatch) | 신규 | 이슈 10 DoD 핵심 미충족 |
| P1 | ai_summary_job 테이블 + summary-status 엔드포인트 | 신규 마이그레이션 + 컨트롤러 | 이슈 6 DoD 미충족 |
| P1 | MockWebServer 타임아웃 테스트 | 신규 | 이슈 3 DoD 미충족 |
| P1 | CB 상태 전이 테스트 | 신규 | 이슈 4 DoD 미충족 |
| P2 | EXPLAIN before/after 측정 및 문서화 | `docs/perf/explain-*.md` | 이슈 8 DoD |
| P2 | Bulk insert 벤치마크 3방식 측정 | `docs/perf/bulk-insert-benchmark.md` | 이슈 11 DoD |
| P2 | k6 실행 및 결과 커밋 | `docs/perf/k6/*.json` | 이슈 12 DoD |
| P2 | ADR 동시성 전략 문서 | `docs/adr/0001-concurrency-strategy.md` | 이슈 10 DoD |
| P2 | spotless 플러그인 설정 | `build.gradle` | 이슈 15 DoD |
| P3 | atomicIncrementCount 데드 코드 정리 또는 실제 사용 | `RequestPlaceAggregateRepository.java` | 코드 위생 |
| P3 | spring-retry @Retryable 배선 또는 의존성 제거 | `RetryConfig.java` | 코드 위생 |

## 8. 측정 실적 요약

| 지표 | P0 이전 | P0 이후 | 출처 |
|------|---------|---------|------|
| 테스트 실행 건수 | 0건 (항상 skip) | 8건 (단위만, Docker 의존 통합 3건 별도) | build/test-results/test/*.xml |
| @CookieValue 수동 파싱 | 10건 | 0건 | grep |
| @LoginUser 적용 컨트롤러 | 1개 | 8개 | grep |
| WebClient 타임아웃 설정 | 0건 | 3개 빈 전수 | WebClientConfig.java, OpenAiClientConfig.java |
| Resilience4j 애노테이션 | 0건 | 3곳 (CB+Retry+Bulkhead) | grep |
| Thread.sleep | 2건 | 0건 | grep |
| onErrorReturn(null) | 1건 | 0건 | grep |
| 조회 경로 AI 동기 호출 | 있음 | 없음 | RequestDetailServiceImpl.java 정독 |
| HomeService 쿼리 수 | O(N) | O(1) | HomeService.java 정독 (실측 없음) |
| StationCsvImportRunner | N select + N insert | 1회 batchUpdate | StationCsvImportRunner.java 정독 (실측 없음) |
| 0.0.0.0/0 (main.tf) | 7건 | 3건 (route table + 8080 + egress) | grep |
| ECR Principal "*" | 있음 | 없음 (role ARN 한정) | main.tf |
| 성능 측정 산출물 | 0건 | 0건 | docs/perf/ |

---

## [기계 판독용 요약]
```json
{
  "overall": "PARTIAL",
  "build_passes": true,
  "tests_executed": 8,
  "test_coverage_pct": null,
  "issues": [
    {"id": 0, "title": "긴급 보안", "verdict": "PARTIAL", "dod_met": "9/12", "blocking_reason": "위조 쿠키 통합 테스트 부재, port 8080 0.0.0.0/0 잔존"},
    {"id": 1, "title": "저장소 위생", "verdict": "PASS", "dod_met": "3/3", "blocking_reason": null},
    {"id": 2, "title": "테스트 정상화", "verdict": "PARTIAL", "dod_met": "5/7", "blocking_reason": "integration 테스트 로컬 미실행, 커버리지 극히 낮음"},
    {"id": 3, "title": "HTTP 타임아웃", "verdict": "PARTIAL", "dod_met": "4/5", "blocking_reason": "MockWebServer 타임아웃 테스트 부재"},
    {"id": 4, "title": "Resilience4j", "verdict": "PARTIAL", "dod_met": "4/5", "blocking_reason": "CB 상태 전이 테스트 부재"},
    {"id": 5, "title": "조회 경로 분리", "verdict": "PARTIAL", "dod_met": "4/6", "blocking_reason": "GroupMapService ensureMocks readOnly 위반, 외부 호출 0건 테스트 부재"},
    {"id": 6, "title": "비동기 파이프라인", "verdict": "PARTIAL", "dod_met": "4/9", "blocking_reason": "트랜잭션 미분리, ai_summary_job 미생성, summary-status 미구현, E2E 테스트 부재"},
    {"id": 7, "title": "Caffeine 캐시", "verdict": "PARTIAL", "dod_met": "4/5", "blocking_reason": "캐시 hit 테스트 부재"},
    {"id": 8, "title": "인덱스 재설계", "verdict": "PARTIAL", "dod_met": "3/5", "blocking_reason": "CONCURRENTLY 미사용, EXPLAIN 산출물 부재"},
    {"id": 9, "title": "HomeService N+1", "verdict": "PARTIAL", "dod_met": "4/5", "blocking_reason": "쿼리 수 단언 테스트 부재"},
    {"id": 10, "title": "동시성", "verdict": "PARTIAL", "dod_met": "3/9", "blocking_reason": "atomicIncrement 데드코드, 동시성 테스트/ADR/ON CONFLICT 부재"},
    {"id": 11, "title": "Bulk Insert", "verdict": "PARTIAL", "dod_met": "3/5", "blocking_reason": "벤치마크 문서, 멱등성 테스트 부재"},
    {"id": 12, "title": "HikariCP + k6", "verdict": "PARTIAL", "dod_met": "4/7", "blocking_reason": "k6 결과 파일, connection-pool-sizing 문서 부재"},
    {"id": 15, "title": "의존성 정리", "verdict": "PARTIAL", "dod_met": "4/5", "blocking_reason": "spotless 미설정"}
  ],
  "critical_unresolved": [
    "AiSummaryServiceImpl @Transactional 안 20초 blocking 호출 — 커넥션 풀 고갈",
    "위조 쿠키 통합 테스트 부재 — 보안 DoD 핵심",
    "성능 측정 산출물 0건 — 모든 수치 UNVERIFIABLE"
  ],
  "new_defects": [
    "GroupMapService readOnly 트랜잭션에서 ensureMocks(쓰기) 호출",
    "atomicIncrementCount 데드 코드",
    "spring-retry @EnableRetry 있으나 @Retryable 0건"
  ],
  "fabricated_metrics": [
    "docs/perf/explain-before.md (미생성)",
    "docs/perf/explain-after.md (미생성)",
    "docs/perf/bulk-insert-benchmark.md (미생성)",
    "docs/perf/connection-pool-sizing.md (미생성)",
    "docs/adr/0001-concurrency-strategy.md (미생성)",
    "docs/perf/k6/*.json (미생성)"
  ],
  "ready_for_phase_b": false
}
```
