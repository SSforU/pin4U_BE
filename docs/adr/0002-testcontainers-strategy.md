# ADR-0002: Testcontainers 전략 및 테스트 환경 분리

## 상태
채택 (2026-08-30)

## 맥락
통합 테스트가 실제 PostgreSQL을 필요로 하지만, 이전까지 Docker 미기동으로 전부 스킵됐다.
H2 같은 인메모리 DB는 Flyway 마이그레이션에 PostgreSQL 전용 문법(pg_trgm, JSONB 등)이
포함되어 있어 사용할 수 없다.

## 검토한 선택지

### (A) 테스트마다 새 컨테이너
- 장점: 테스트 격리 완벽
- 단점: 매 테스트 클래스마다 PostgreSQL 기동(~5초)이 반복되어 전체 시간이 N배

### (B) 싱글톤 컨테이너 + withReuse
- 장점: 한 번 기동한 컨테이너를 전 테스트가 공유, JVM 종료 후에도 재사용 가능
- 단점: 테스트 간 데이터 오염 가능성 (트랜잭션 롤백 또는 @DirtiesContext로 완화)

### (C) docker-compose의 DB를 직접 사용
- 장점: 컨테이너 기동 오버헤드 0
- 단점: 로컬 DB 상태에 의존, CI 환경 재현 불가, 포트 충돌 가능

## 결정
**(B) 싱글톤 컨테이너 + withReuse를 채택한다.**

```java
private static final PostgreSQLContainer<?> POSTGRES = ...withReuse(true);
static { POSTGRES.start(); }
```

`@ServiceConnection`으로 Spring Boot가 자동으로 datasource URL을 주입한다.

## 근거
1. 전체 통합 테스트 스위트가 하나의 컨테이너를 공유해 기동 시간이 1회(~5초)로 수렴.
2. `withReuse(true)` + `~/.testcontainers.properties`의 `testcontainers.reuse.enable=true`로
   JVM 재시작 후에도 기존 컨테이너를 재사용한다.
3. Spring Boot 3.1+의 `@ServiceConnection`이 JDBC URL, 사용자, 비밀번호를 자동 주입하므로
   `application.yml`에 테스트 DB 설정을 수동으로 관리할 필요가 없다.
4. 테스트 태그 분리(`@Tag("integration")`)로 `./gradlew test`는 Docker 없이 실행 가능하고,
   `./gradlew integrationTest`만 Docker를 요구한다.

## 감수한 트레이드오프
- 테스트 간 데이터 격리가 완벽하지 않다. `@Transactional` 테스트는 자동 롤백되지만,
  `@SpringBootTest(webEnvironment=RANDOM_PORT)` 등 별도 트랜잭션으로 도는 테스트는
  명시적 cleanup이 필요하다.
- `withReuse(true)` 사용 시 `~/.testcontainers.properties` 설정이 필요함을 팀원에게 안내해야 한다.
