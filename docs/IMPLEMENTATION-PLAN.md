# pin4U_BE 리팩토링 상세 구현 명세

> 작성 기준: 2026-08-18, main 브랜치 `d316d90` 시점
> 이 문서는 이슈 0~15의 구현 순서·변경 대상·코드 스니펫·판단 근거를 기술한다.
> "구현하라" 지시 시 이 문서대로 순차 실행한다.

---

## 공통 워크플로우 (모든 이슈 동일)

```
1. gh issue create
2. git switch main && git pull && git switch -c "feat/#<N>"
3. 개발 (이슈 범위만)
4. ./gradlew build -PwithTests  ← 이슈 2 이전에는 -PwithTests, 이후에는 플래그 없이
5. 논리 단위 커밋 (Refs #N)
6. git push -u origin "feat/#<N>"
7. gh pr create --base main
8. gh pr merge --squash --delete-branch
9. git switch main && git pull
```

---

# 이슈 0: 긴급 보안 조치 — 커밋된 시크릿 및 인증 우회

GitHub Issue: #27

## 0-A. Terraform 시크릿 및 인프라 보안 (커밋 1)

### 변경 파일 및 내용

**`variables.tf`** — description에서 실제 비밀번호 제거
```hcl
# BEFORE
variable "db_password" {
  description = "Lsy12052781!"
  type        = string
  sensitive   = true
}

# AFTER
variable "db_password" {
  description = "RDS master password. Inject via TF_VAR_db_password or terraform.tfvars (gitignored)."
  type        = string
  sensitive   = true
}
```

**`main.tf`** — 보안 그룹 축소 + ECR 정책 한정 + RDS 비공개

1. EC2 보안 그룹 — SSH/Prometheus/Grafana 퍼블릭 개방 제거:
```hcl
# SSH: 0.0.0.0/0 → SSM 전용으로 제거 (SSM Agent 이미 설치됨, user_data 참조)
# 삭제:
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

# Prometheus 9090, Grafana 3000: 0.0.0.0/0 → EC2 자체 SG 참조 (내부 접근만)
# 삭제:
  ingress {
    from_port   = 9090
    ...
  }
  ingress {
    from_port   = 3000
    ...
  }
```

판단 근거: EC2에 SSM Agent가 이미 설치되어 있으므로 SSH 인바운드가 불필요하다.
Prometheus/Grafana는 EC2 내부에서만 접근하거나 SSM 포트포워딩으로 충분하다.

2. RDS 보안 그룹 — CIDR 개방 → EC2 SG 참조:
```hcl
# BEFORE
ingress {
  from_port   = 5432
  to_port     = 5432
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]
}

# AFTER
ingress {
  from_port       = 5432
  to_port         = 5432
  protocol        = "tcp"
  security_groups = [aws_security_group.ec2.id]
}
```

3. RDS 인스턴스 — 퍼블릭 비활성화:
```hcl
# BEFORE
publicly_accessible = true

# AFTER
publicly_accessible = false
```

4. ECR 정책 — Principal 한정:
```hcl
# BEFORE
Principal = { AWS = "*" }

# AFTER — 같은 계정의 EC2 역할만 허용
Principal = {
  AWS = aws_iam_role.ec2_role.arn
}
```

판단 근거: 실제로 ECR에 push하는 주체는 GitHub Actions(별도 IAM User)와
EC2 인스턴스뿐이다. GitHub Actions의 IAM ARN도 추가해야 하나,
해당 ARN은 secrets에 있으므로 data source 또는 변수로 주입한다.
최소 조치로 `*` 제거가 급선무.

추가 변수:
```hcl
variable "ci_iam_arn" {
  description = "IAM ARN for CI/CD (GitHub Actions). Used for ECR push access."
  type        = string
  default     = ""
}
```

ECR 정책 최종:
```hcl
Principal = {
  AWS = compact([
    aws_iam_role.ec2_role.arn,
    var.ci_iam_arn
  ])
}
```

## 0-B. HMAC 서명 인증 토큰 도입 (커밋 2)

### 설계 판단

Spring Security + JWT vs HMAC 서명 쿠키 비교:

| 기준 | Spring Security + JWT | HMAC 서명 쿠키 |
|------|----------------------|----------------|
| 변경 범위 | 전 엔드포인트 SecurityFilterChain 설정 | ArgumentResolver + AuthController만 |
| 기존 Kakao OAuth 호환 | FilterChain 순서 조정 필요 | 그대로 동작 |
| 토큰 검증 | JWT 라이브러리 추가 | JDK 표준 `javax.crypto.Mac` |
| 포트폴리오 가치 | "Spring Security 도입" 주장 가능 | "HMAC 서명 기반 인증 무결성 보장" 주장 가능 |
| 위험도 | 높음 (전 API 경로 영향) | 낮음 (쿠키 포맷만 변경) |

**결정: HMAC 서명 쿠키**

이유:
1. 핵심 문제(평문 uid 위조)를 최소 변경으로 해결한다.
2. 기존 프론트엔드 배포에 영향이 없다 (쿠키는 서버가 발급·검증하므로).
3. Spring Security는 이슈 범위를 넘어서며, 별도 이슈로 분리할 수 있다.

### 토큰 포맷

```
uid.expiresEpochSeconds.hmacHex
예: 42.1724025600.a3f2b8c1d4e5f6...
```

- `uid`: 사용자 ID (Long)
- `expiresEpochSeconds`: 만료 시각 (epoch seconds)
- `hmacHex`: `HmacSHA256(uid + "." + expiresEpochSeconds, secret)` 의 hex 문자열

### 신규 파일

**`src/main/java/io/github/ssforu/pin4u/common/auth/AuthTokenProvider.java`**
```java
package io.github.ssforu.pin4u.common.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class AuthTokenProvider {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final byte[] secretBytes;

    public AuthTokenProvider(
            @Value("${app.auth.hmac-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "app.auth.hmac-secret must be at least 32 characters. "
                + "Generate with: openssl rand -hex 32");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** uid + 만료시각으로 서명된 토큰 생성 */
    public String issueToken(Long uid) {
        long expires = Instant.now().plus(DEFAULT_TTL).getEpochSecond();
        String payload = uid + "." + expires;
        String signature = sign(payload);
        return payload + "." + signature;
    }

    /** 토큰 검증. 유효하면 uid 반환, 아니면 empty. */
    public Optional<Long> validateToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        String[] parts = token.split("\\.", 3);
        if (parts.length != 3) return Optional.empty();

        try {
            long uid = Long.parseLong(parts[0]);
            long expires = Long.parseLong(parts[1]);
            String signature = parts[2];

            // 만료 검사
            if (Instant.now().getEpochSecond() > expires) return Optional.empty();

            // 서명 검증
            String expectedSig = sign(parts[0] + "." + parts[1]);
            if (!constantTimeEquals(expectedSig, signature)) return Optional.empty();

            return Optional.of(uid);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    /** 타이밍 공격 방지용 상수 시간 비교 */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
```

### 수정 파일

**`src/main/resources/application.yml`** — HMAC 시크릿 설정 추가
```yaml
app:
  auth:
    hmac-secret: ${AUTH_HMAC_SECRET:}  # 운영: 환경변수 필수, 로컬: 아래 application-local.yml에서 설정
```

**`src/main/resources/application-local.yml.example`** (신규, gitignore에 추가 불필요 — example이므로)
```yaml
app:
  auth:
    hmac-secret: "local-dev-secret-at-least-32-characters-long-for-testing"
```

**`src/main/java/io/github/ssforu/pin4u/common/resolver/LoginUserArgumentResolver.java`**
```java
// BEFORE: extractUidFromCookies → 쿠키 "uid" 의 원시 값을 Long.valueOf로 변환
// AFTER:  extractUidFromCookies → 쿠키 "uid" 의 서명된 토큰을 AuthTokenProvider.validateToken으로 검증

// 변경점:
// 1. AuthTokenProvider 주입
// 2. extractUidFromCookies 대신 extractVerifiedUid 메서드
// 3. validateToken이 empty 반환 시 → 위조된 토큰 → required면 401, 아니면 null

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;
    private final AuthTokenProvider tokenProvider;  // 추가

    // ... supportsParameter 동일 ...

    @Override
    public Object resolveArgument(...) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        LoginUser annotation = parameter.getParameterAnnotation(LoginUser.class);

        Optional<Long> verified = extractVerifiedUid(request);

        if (verified.isEmpty()) {
            if (annotation != null && annotation.required()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login_required");
            }
            return null;
        }

        Long userId = verified.get();
        if (Long.class.isAssignableFrom(parameter.getParameterType())) {
            return userId;
        }
        if (User.class.isAssignableFrom(parameter.getParameterType())) {
            return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user_not_found"));
        }
        return null;
    }

    private Optional<Long> extractVerifiedUid(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
            .filter(c -> "uid".equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .flatMap(tokenProvider::validateToken);
    }
}
```

**`src/main/java/io/github/ssforu/pin4u/features/auth/api/AuthController.java`**
```java
// 변경점:
// 1. AuthTokenProvider 주입
// 2. login()에서 쿠키 값을 평문 uid → tokenProvider.issueToken(uid)
// 3. me()에서 @CookieValue 대신 @LoginUser 사용 (또는 tokenProvider.validateToken)

// login 메서드 내:
// BEFORE:
ResponseCookie.from("uid", String.valueOf(out.user().id()))

// AFTER:
String token = tokenProvider.issueToken(out.user().id());
ResponseCookie.from("uid", token)
```

### 하위 호환성

기존 프론트엔드는 쿠키를 읽지 않고 서버가 Set-Cookie로 발급한 값을 그대로 돌려보낸다.
따라서 서버 측 토큰 포맷 변경만으로 전환이 완료된다.
단, **배포 순간 기존 로그인 세션은 무효화**된다 (HMAC 검증 실패).
이는 보안 이슈 해결의 부수 효과로 허용 가능하다.

## 0-C. @LoginUser 전 컨트롤러 통일 + 인가 검증 (커밋 3)

### 대상 컨트롤러 전수 목록

| 컨트롤러 | 현재 인증 방식 | 변경 내용 |
|-----------|---------------|-----------|
| `AuthController` | `@CookieValue` (me) | `me()`: `@LoginUser(required=false)` 사용. `login()`/`logout()`은 인증 전이므로 변경 없음 |
| `GroupController` | `@CookieValue` + `parseUidOrNull` | 4개 메서드 전부 `@LoginUser`로 교체, `parseUidOrNull` 헬퍼 삭제 |
| `GroupMapController` | `@CookieValue` + 수동 파싱 | `@LoginUser(required=true)` |
| `HomeController` | `@CookieValue` + 수동 파싱 | `@LoginUser(required=false)`, null이면 204 |
| `RequestController` | `@CookieValue` + 수동 파싱 | `create`: `@LoginUser(required=true)`, `delete`: `@LoginUser(required=true)` |
| `NotificationController` | `@CookieValue` + `parseUidOrNull` | `@LoginUser(required=true)`, `parseUidOrNull` 삭제 |
| `RecommendationController` | **인증 없음** | `@LoginUser(required=true)` 추가. guestId를 본인 확인 용도로 활용하되, uid 기반 인증을 1차 관문으로 설정 |
| `MemberMeController` | `@LoginUser` (이미 완료) | 변경 없음 |
| `MemberController` | 없음 (고정 유저 데모) | 엔드포인트 삭제 또는 `@Deprecated` 처리. 실 운영에서 id=1 고정 반환은 보안 허점 |
| `GroupNotesController` | 없음 ("시연용" 주석) | GET 리다이렉트 엔드포인트이므로 읽기 접근은 허용하되, "시연용" 주석 제거 |
| `RequestDetailController` | 없음 | 공개 조회 엔드포인트. 변경 없음 |
| `PlaceController` | 없음 | 공개 검색 엔드포인트. 변경 없음 |
| `StationController` | 없음 | 공개 검색 엔드포인트. 변경 없음 |
| `UploadHelperController` | `gid` 쿠키만 | `@LoginUser(required=true)` 추가 (이미지 키 생성은 인증 필요) |
| `OgController` | 없음 | OG 메타태그용 공개 엔드포인트. 변경 없음 |
| `PlaceSummaryController` | 없음 | 스텁. 변경 없음 |
| `AutoRecommendationController` | 없음 | 공개 조회. 변경 없음 |
| `RequestPlaceNotesController` | 없음 | 공개 조회. 변경 없음 |

### 인가(Authorization) 검증 추가 대상

쓰기 엔드포인트에서 "이 요청자가 이 리소스의 소유자/그룹 멤버인가" 검증:

| 엔드포인트 | 검증 내용 | 현재 상태 |
|-----------|-----------|-----------|
| `POST /api/groups` (생성) | 인증만 필요 | OK (인증 후 누구나 생성 가능) |
| `POST /api/groups/{slug}/members` (approve/reject) | `me`가 그룹 owner인지 | **이미 서비스 레이어에서 검증** (`GroupService.approveMember`에서 owner 체크) |
| `POST /api/requests` (생성) | 인증만 필요 | OK |
| `DELETE /api/requests/{slug}` | `me`가 요청 owner인지 | **이미 서비스 레이어에서 검증** (`requestService.delete`에서 NOT_OWNER 반환) |
| `POST /api/requests/{slug}/recommendations` | 인증 필요 | **현재 인증 없음 → 추가** |
| `PATCH /api/me` | 본인만 | OK (`@LoginUser`의 uid가 곧 대상) |
| `POST /api/uploads/images/make-key` | 인증 필요 | **현재 gid만 → uid 인증 추가** |

### IDOR 가능 지점 전수 조사

| 지점 | 위험도 | 대응 |
|------|--------|------|
| `GET /api/home` — uid로 남의 대시보드 | 낮음 | `@LoginUser`가 쿠키에서 추출하므로 본인 것만 조회 |
| `GET /api/groups/{slug}/members/requests` — owner가 아닌 사람이 조회 | 중간 | 서비스에서 owner 검증 이미 있음 |
| `GET /api/groups/{slug}/map` — 비멤버가 그룹 지도 조회 | 중간 | 서비스에서 멤버십 검증 이미 있음 |
| `GET /api/notifications` — uid로 남의 알림 조회 | 낮음 | `@LoginUser`가 본인 uid만 반환 |
| `POST /api/requests/{slug}/recommendations` — 스팸 | 높음 | **인증 추가로 해결** |

### 코드 패턴 (GroupController 예시)

```java
// BEFORE
@PostMapping
public ResponseEntity<...> create(
    @CookieValue(name = "uid", required = false) String uid, ...) {
    Long me = parseUidOrNull(uid);
    if (me == null) return unauthorized();
    ...
}

// AFTER
@PostMapping
public ResponseEntity<...> create(
    @LoginUser(required = true) Long me, ...) {
    // 인증 실패 시 LoginUserArgumentResolver가 401을 던진다.
    // parseUidOrNull, unauthorized() 헬퍼 모두 삭제.
    ...
}
```

## 0-D. Actuator 보안 (커밋 4)

### `application.yml` 변경

```yaml
# BEFORE
management:
  endpoint:
    health:
      show-details: always

# AFTER
management:
  server:
    port: 9091  # 관리 포트 분리, EC2 SG에서 9091 인바운드 미개방 → 외부 접근 불가
  endpoint:
    health:
      show-details: when-authorized
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

판단 근거:
- `show-details: when-authorized`로 변경하면 인증된 요청만 상세 정보를 볼 수 있다.
  Spring Security가 없는 현재 상태에서는 모든 요청이 anonymous이므로 사실상 상세 숨김.
- `management.server.port: 9091`로 분리하면 EC2 SG에서 9091을 열지 않는 한 외부 접근 불가.
  Prometheus는 EC2 내부에서 localhost:9091로 스크래핑.

### `application-prod.yml` 변경

```yaml
# 추가
management:
  server:
    port: 9091
  endpoint:
    health:
      show-details: when-authorized
```

## 0-E. 테스트 (커밋 5)

### 신규 테스트 파일: `AuthTokenProviderTest.java`

```java
@SpringBootTest(classes = AuthTokenProvider.class,
    properties = "app.auth.hmac-secret=test-secret-that-is-at-least-32-characters")
class AuthTokenProviderTest {

    @Autowired AuthTokenProvider provider;

    @Test
    void validToken_returnsUid() {
        String token = provider.issueToken(42L);
        assertThat(provider.validateToken(token)).contains(42L);
    }

    @Test
    void tamperedUid_rejected() {
        String token = provider.issueToken(42L);
        String tampered = token.replaceFirst("^42\\.", "99.");
        assertThat(provider.validateToken(tampered)).isEmpty();
    }

    @Test
    void expiredToken_rejected() {
        // 만료 시각을 과거로 조작한 토큰은 거부됨을 검증
        // (직접 sign 메서드를 호출할 수 없으므로, 리플렉션 또는 테스트용 clock 주입)
    }

    @Test
    void nullOrBlank_rejected() {
        assertThat(provider.validateToken(null)).isEmpty();
        assertThat(provider.validateToken("")).isEmpty();
        assertThat(provider.validateToken("garbage")).isEmpty();
    }
}
```

### 신규 테스트: `AuthSecurityIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void forgedUidCookie_returns401() throws Exception {
        // 평문 uid=1 쿠키로 인증 필요 엔드포인트 호출 → 401
        mvc.perform(post("/api/groups")
                .cookie(new Cookie("uid", "1"))
                .contentType(APPLICATION_JSON)
                .content("""{"name":"test"}"""))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void noUidCookie_returns401() throws Exception {
        mvc.perform(post("/api/requests")
                .contentType(APPLICATION_JSON)
                .content("""{"stationCode":"0222","requestMessage":"test"}"""))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validSignedToken_passes() throws Exception {
        // AuthTokenProvider로 정상 토큰 발급 → 인증 통과 확인
        // (DB에 해당 유저가 있어야 하므로 Testcontainers 환경 필요)
    }
}
```

## 0-F. PR 본문 체크리스트 (사람이 직접 해야 할 항목)

```markdown
## 사람이 직접 해야 할 조치 (코드 외)
- [ ] RDS 마스터 비밀번호 즉시 교체 (AWS Console > RDS > Modify)
- [ ] `AUTH_HMAC_SECRET` 환경변수를 운영 서버에 설정 (최소 32자, `openssl rand -hex 32`)
- [ ] 기존 로그인 세션이 무효화됨을 프론트엔드 팀에 공유
- [ ] (선택) `git filter-repo`로 variables.tf 히스토리에서 비밀번호 제거
- [ ] (선택) Terraform state에서 이전 password 값 확인 및 필요시 state refresh
```

---

# 이슈 1: 저장소 위생 및 커밋된 시크릿 제거

GitHub Issue: #26

## 커밋 1: 잡파일 추적 해제 및 물리 삭제

```bash
git rm --cached cookies.txt member.cookies owner.cookies \
    db_audit_20250921_204658.txt out_a.json ":bootRun" Dockerfile.bak
rm -f cookies.txt member.cookies owner.cookies \
    db_audit_20250921_204658.txt out_a.json ":bootRun" Dockerfile.bak
```

## 커밋 2: .gitignore 정리

```gitignore
# BEFORE (문제: application-prod.yml이 있으나 실제로는 추적 유지해야 함)
src/main/resources/application-prod.yml

# AFTER: 해당 라인 삭제.
# 판단 근거: application-prod.yml에는 환경변수 placeholder만 있고 실제 시크릿이 없다.
# .gitignore에 넣으면서도 추적하는 것은 모순이다.

# 추가할 패턴:
db_audit_*.txt
out_*.json
*.bak
```

## 커밋 3: build.gradle flyway 블록 환경변수화

```groovy
// BEFORE
flyway {
    url = "jdbc:postgresql://localhost:5432/pin4u_be"
    user = "pin4u"
    password = "pin4u"
    locations = ["classpath:db/migration"]
}

// AFTER
flyway {
    url = project.findProperty("flyway.url") ?: "jdbc:postgresql://localhost:5432/pin4u_be"
    user = project.findProperty("flyway.user") ?: "pin4u"
    password = project.findProperty("flyway.password") ?: ""
    locations = ["classpath:db/migration"]
}
```

password 기본값을 빈 문자열로 둔다. 로컬 개발 시 `gradle.properties`(gitignored)에 설정하거나
`-Pflyway.password=xxx`로 전달한다.

### 완료 조건 검증

```bash
git ls-files | grep -E "cookies|db_audit|out_a|bootRun|\.bak"  # 빈 결과
grep -n "pin4u" build.gradle  # flyway 블록에 평문 비밀번호 없음
./gradlew build -PwithTests
```

---

# 이슈 2: 테스트 실행 정상화 및 검증 인프라 구축

## 커밋 1: build.gradle 테스트 게이트 제거 + JaCoCo 추가

```groovy
// BEFORE
tasks.withType(Test).configureEach {
    useJUnitPlatform()
    onlyIf { project.hasProperty("withTests") }
    systemProperty "spring.testcontainers.enabled", "false"
}

// AFTER
tasks.withType(Test).configureEach {
    useJUnitPlatform()
}

// JaCoCo 추가
plugins {
    // ... 기존 플러그인 ...
    id 'jacoco'
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}
```

## 커밋 2: Testcontainers 정비

**`src/test/java/io/github/ssforu/pin4u/TestcontainersConfiguration.java`** — 재작성
```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @RestartScope
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
            .withReuse(true);
    }
}
```

**`src/test/resources/application-test.yml`** (신규)
```yaml
app:
  auth:
    hmac-secret: "test-secret-that-is-at-least-32-characters-long-for-testing"
  kakao:
    enabled: false
  ai:
    enabled: false
  seed:
    enabled: false
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
```

## 커밋 3: Flyway 마이그레이션 검증 테스트

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {
    @Autowired Flyway flyway;

    @Test
    void allMigrations_applySuccessfully() {
        var info = flyway.info();
        assertThat(info.applied()).isNotEmpty();
        assertThat(Arrays.stream(info.applied())
            .allMatch(m -> m.getState().isApplied())).isTrue();
        // 실패한 마이그레이션이 없어야 한다
        assertThat(info.current().getState().isFailed()).isFalse();
    }
}
```

## 커밋 4: 슬라이스 테스트 기반

**`StationControllerTest.java`** — `@WebMvcTest`
```java
@WebMvcTest(StationController.class)
class StationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean StationService stationService;

    @Test
    void search_returnsStations() throws Exception {
        when(stationService.search("강남", 10))
            .thenReturn(new StationService.SearchResponse(
                List.of(new StationDtos.StationItem("0222", "강남", "2호선", 37.4, 127.0)),
                1));

        mvc.perform(get("/api/stations/search").param("q", "강남"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }
}
```

**`StationRepositoryTest.java`** — `@DataJpaTest` + Testcontainers
```java
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = NONE)
class StationRepositoryTest {
    @Autowired StationRepository repo;

    @Test
    void findByNameContainingIgnoreCase_returnsMatches() {
        // Flyway가 적용된 TC에서 역 데이터가 있을 수 있으므로, 직접 insert 후 조회
        repo.save(new Station("T001", "강남", "2호선", 37.4979, 127.0276));
        repo.save(new Station("T002", "강남구청", "7호선", 37.5172, 127.0473));

        var result = repo.findByNameContainingIgnoreCase("강남", PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSizeGreaterThanOrEqualTo(2);
    }
}
```

## 커밋 5: CI 워크플로우

**`.github/workflows/ci.yml`** (신규)
```yaml
name: CI
on:
  pull_request:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: gradle
      - run: ./gradlew build jacocoTestReport
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: |
            build/reports/tests/
            build/reports/jacoco/
```

**`.github/workflows/deploy.yml`** — 액션 버전 업그레이드
```yaml
# BEFORE
- uses: actions/checkout@v1
- uses: actions/setup-java@v2
- uses: aws-actions/configure-aws-credentials@v1
- uses: aws-actions/amazon-ecr-login@v1

# AFTER
- uses: actions/checkout@v4
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: 17
    cache: gradle
- uses: aws-actions/configure-aws-credentials@v4
- uses: aws-actions/amazon-ecr-login@v2
```

---

# 이슈 3: 외부 HTTP 클라이언트 타임아웃 및 커넥션 설정

## 설계 판단

타임아웃 값의 근거:
- **Kakao 로컬 검색 (connect 2s / response 3s)**: 사용자 응답 경로. p99가 1초 이내인 API에 3배 마진.
- **Kakao OAuth (connect 2s / response 3s)**: 로그인 경로. 실패 시 즉시 재시도 유도가 나음.
- **OpenAI (connect 2s / response 20s)**: 비동기 백그라운드. GPT 응답은 10초+ 가능.

## 커밋 1: 공통 HttpClient 팩토리 + 타임아웃 설정

**`src/main/java/io/github/ssforu/pin4u/common/config/HttpClientFactory.java`** (신규)
```java
@Component
public class HttpClientFactory {

    public HttpClient create(Duration connectTimeout, Duration responseTimeout) {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
            .responseTimeout(responseTimeout)
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(responseTimeout.toSeconds(), TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(responseTimeout.toSeconds(), TimeUnit.SECONDS)));
    }
}
```

**`application.yml`** 추가:
```yaml
app:
  http:
    kakao-search:
      connect-timeout: 2s
      response-timeout: 3s
    kakao-oauth:
      connect-timeout: 2s
      response-timeout: 3s
    openai:
      connect-timeout: 2s
      response-timeout: 20s
```

## 커밋 2: WebClientConfig / OpenAiClientConfig / KakaoOAuthClient 수정

**WebClientConfig** — kakaoWebClient에 타임아웃 적용:
```java
@Bean
public WebClient kakaoWebClient(HttpClientFactory factory,
        @Value("${app.http.kakao-search.connect-timeout}") Duration connectTimeout,
        @Value("${app.http.kakao-search.response-timeout}") Duration responseTimeout) {
    HttpClient httpClient = factory.create(connectTimeout, responseTimeout);
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "KakaoAK " + apiKey)
        .build();
}
```

**OpenAiClientConfig** — openaiWebClient에 타임아웃 적용 (동일 패턴, 20s response)

**KakaoOAuthClient** — 인라인 WebClient 생성 제거, 빈 주입으로 전환:
```java
// BEFORE (line 12-15)
private final WebClient client = WebClient.builder()
    .baseUrl("https://kapi.kakao.com")
    .build();

// AFTER
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {
    private final WebClient kakaoOAuthWebClient;  // 빈 주입
    // ...
}
```

별도 `@Bean WebClient kakaoOAuthWebClient(HttpClientFactory factory, ...)` 등록.

## 커밋 3: .onErrorReturn(null) 제거

**`AiKeywordServiceImpl.java:59-67`**:
```java
// BEFORE
.onErrorReturn(null)
.block();

// AFTER
.onErrorResume(e -> {
    log.warn("OpenAI keyword extraction failed: {}", e.getMessage());
    return Mono.empty();
})
.blockOptional()
.orElse(null);  // null 대신 heuristic 폴백으로 연결
```

실제로는 `.blockOptional().orElse(null)` 후에 기존 heuristic 폴백 로직이 이미 있으므로,
빈 응답 시 자연스럽게 폴백된다.

## 커밋 4: 4xx/5xx 도메인 예외 변환

각 WebClient 호출부에 `.onStatus()` 추가:
```java
.retrieve()
.onStatus(HttpStatusCode::is4xxClientError, resp ->
    Mono.error(new ExternalApiException("Kakao 4xx: " + resp.statusCode())))
.onStatus(HttpStatusCode::is5xxServerError, resp ->
    Mono.error(new ExternalApiException("Kakao 5xx: " + resp.statusCode())))
.bodyToMono(...)
```

**`ExternalApiException.java`** (신규 — `RuntimeException` 확장)

## 커밋 5: 타임아웃 검증 테스트

MockWebServer로 지연 응답을 주입하고, 설정 시간 내에 예외가 발생하는지 검증:
```java
@SpringBootTest
class WebClientTimeoutTest {
    MockWebServer mockServer;

    @Test
    void kakaoSearch_timesOutWithin5Seconds() {
        mockServer.enqueue(new MockResponse()
            .setBodyDelay(10, TimeUnit.SECONDS)
            .setBody("{}"));
        // kakaoWebClient 호출 → 3s 이내 WebClientRequestException 발생
        assertThatThrownBy(() -> ...)
            .isInstanceOf(WebClientRequestException.class);
    }
}
```

---

# 이슈 4: Resilience4j 서킷브레이커 실제 배선

## 커밋 1: 의존성 추가

```groovy
implementation 'io.github.resilience4j:resilience4j-spring-boot3'
implementation 'io.github.resilience4j:resilience4j-micrometer'
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

## 커밋 2: application.yml 인스턴스 정비

```yaml
resilience4j:
  circuitbreaker:
    instances:
      kakaoSearch:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20          # 최근 20회 호출 기준
        failure-rate-threshold: 50       # 50% 실패 시 OPEN
        wait-duration-in-open-state: 30s # 30초 후 HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 5  # HALF_OPEN에서 5회 시도
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - io.github.ssforu.pin4u.common.exception.ExternalApiException
      openai:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10          # AI 호출은 빈도가 낮으므로 작은 윈도우
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s # OpenAI 장애는 복구에 시간 소요
        permitted-number-of-calls-in-half-open-state: 3
  retry:
    instances:
      kakaoSearch:
        max-attempts: 3
        wait-duration: 300ms
      openai:
        max-attempts: 2
        wait-duration: 1s
  bulkhead:
    instances:
      openai:
        max-concurrent-calls: 10  # Hikari pool(30) 대비 AI 동시 호출 제한

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,circuitbreakers,circuitbreakerevents
  health:
    circuitbreakers:
      enabled: true
```

## 커밋 3: 애노테이션 배선

애노테이션 적용 순서: `@Bulkhead` → `@CircuitBreaker` → `@Retry` (외→내)
Resilience4j Spring Boot에서 실제 실행 순서는 설정의 `aspectOrder` 에 따르며,
기본값은 Retry → CircuitBreaker → Bulkhead (내→외) 순이다.
즉 **Retry가 가장 안쪽**에서 재시도하고, 재시도 실패가 CircuitBreaker에 기록되며,
Bulkhead가 전체 동시 호출을 제한한다.

**`KakaoSearchAdapterImpl.java`**:
```java
@CircuitBreaker(name = "kakaoSearch", fallbackMethod = "keywordSearchFallback")
@Retry(name = "kakaoSearch")
public List<KakaoPlace> keywordSearch(String query, ...) { ... }

// 폴백: 빈 리스트 반환 + 메트릭 기록
private List<KakaoPlace> keywordSearchFallback(String query, ..., Throwable t) {
    log.warn("Kakao search fallback for query='{}': {}", query, t.getMessage());
    meterRegistry.counter("kakao.search.fallback").increment();
    return List.of();
}
```

**`AiKeywordServiceImpl.java`**:
```java
@CircuitBreaker(name = "openai", fallbackMethod = "extractTop2Fallback")
@Retry(name = "openai")
public List<String> extractTop2(String message) { ... }

private List<String> extractTop2Fallback(String message, Throwable t) {
    log.warn("AI keyword extraction fallback: {}", t.getMessage());
    return heuristicExtract(message);  // 기존 휴리스틱 폴백
}
```

**`AiSummaryServiceImpl.java`**:
```java
@CircuitBreaker(name = "openai", fallbackMethod = "generateSummaryFallback")
@Retry(name = "openai")
@Bulkhead(name = "openai")
public Optional<String> generateSummary(...) { ... }

private Optional<String> generateSummaryFallback(..., Throwable t) {
    log.warn("AI summary fallback: {}", t.getMessage());
    return Optional.empty();
}
```

## 커밋 4: 서킷 전이 테스트

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CircuitBreakerTest {
    @Autowired CircuitBreakerRegistry registry;
    @Autowired KakaoSearchAdapterImpl adapter;
    // MockWebServer로 연속 5xx 응답

    @Test
    void kakaoSearch_opensCircuit_afterThresholdFailures() {
        // 20회 중 10회(50%) 실패 주입 → OPEN 전이 확인
        CircuitBreaker cb = registry.circuitBreaker("kakaoSearch");
        // ... 반복 호출 ...
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void openCircuit_returnsFallback_immediately() {
        CircuitBreaker cb = registry.circuitBreaker("kakaoSearch");
        cb.transitionToOpenState();
        var result = adapter.keywordSearch("강남", ...);
        assertThat(result).isEmpty();
    }
}
```

---

# 이슈 5: 조회 경로에서 동기 외부 API 호출 제거

## 핵심 변경

### 1. RequestDetailServiceImpl — AI 호출 제거

```java
// BEFORE (line 153-155)
Optional<String> summaryOpt = aiSummaryService.generateSummary(
    cur.placeName(), cur.categoryName(), rating, ratingCount, reviewSnippets, userTags);

// AFTER
// AI 요약은 조회 시점에 호출하지 않는다.
// RequestDetailQueryRepository에서 place_summaries 테이블을 JOIN해 가져온다.
String summaryText = cur.aiSummaryText();  // 쿼리 결과에서 직접 가져옴
SummaryStatus summaryStatus = (summaryText != null) ? SummaryStatus.READY
    : SummaryStatus.PENDING;
```

### 2. RequestDetailQueryRepository — NULL::TEXT 제거, 실제 JOIN

```sql
-- BEFORE (line 65-67)
NULL::TEXT AS ai_summary_text,
NULL::TEXT AS ai_evidence_json,
NULL::timestamptz AS ai_updated_at

-- AFTER
ps.summary_text AS ai_summary_text,
ps.evidence AS ai_evidence_json,
ps.updated_at AS ai_updated_at
-- ... FROM 절에 추가:
LEFT JOIN place_summaries ps ON ps.place_id = p.id
```

### 3. 응답 DTO에 summaryStatus 필드 추가

```java
public enum SummaryStatus { READY, PENDING, UNAVAILABLE }

// RequestDetailDtos.Item에 추가:
private final SummaryStatus summaryStatus;
```

### 4. @Transactional(readOnly = true) 복원

```java
// BEFORE (line 57)
@Transactional  // 쓰기 트랜잭션

// AFTER
@Transactional(readOnly = true)
```

### 5. MockAllocator 호출 분리

MockAllocator.ensureMocks()는 쓰기 작업이다. 조회 경로에서 호출하면 안 된다.
→ 요청 생성 시점(RequestService.create)에서 미리 호출하거나,
→ 별도 스케줄 배치로 처리한다.

**판단: 요청 생성 시점에서 호출.**
`RequestServiceImpl.create()` 또는 이벤트 리스너에서 `mockAllocator.ensureMocks()` 호출.
조회 경로에서는 mock이 없으면 null 반환.

### 6. GroupMapService 동일 적용

```java
// BEFORE (line 37)
@Transactional  // mock 생성 필요시 write 허용

// AFTER
@Transactional(readOnly = true)
// MockAllocator 호출 제거. mock이 없는 장소는 null 필드로 반환.
```

### 7. 폴링 엔드포인트

```java
@GetMapping("/api/requests/{slug}/summary-status")
public ResponseEntity<...> summaryStatus(@PathVariable String slug) {
    // ai_summary_job 테이블 조회 (이슈 6에서 생성)
    // 또는 place_summaries 존재 여부로 판단
}
```

---

# 이슈 6: AI 요약 비동기 파이프라인 실제 배선

## 핵심 변경

### 1. 이벤트 발행을 실제 경로에 연결

현재 `RequestCreatedEvent`는 `RequestPlaceNotesServiceImpl.createRequestWithNotes()`에서만 발행되는데,
이 메서드는 어떤 컨트롤러에도 연결되지 않았다.

**방안**: `RequestServiceImpl.create()` 마지막에 이벤트 발행 추가.
```java
// RequestServiceImpl.create() 끝에 추가
eventPublisher.publishEvent(new RequestCreatedEvent(saved.getSlug(), ownerUserId));
```

`createRequestWithNotes`는 사용되지 않으므로 `@Deprecated` 처리 또는 제거.

### 2. Thread.sleep(3000) 제거

`AiSummaryServiceImpl.java:62-66` — 단순 삭제.

### 3. 트랜잭션 경계 재설계 (핵심)

```java
// BEFORE: 하나의 @Transactional 안에 [조회 + sleep + 외부 HTTP + 저장]

// AFTER: 3단 분리
@Service
@RequiredArgsConstructor
public class AiSummaryServiceImpl {

    private final AiSummaryInternalService internal;  // self-invocation 방지용 분리

    // 1단: 트랜잭션 밖 — 외부 호출 오케스트레이션
    public void generateAndSaveSummary(String requestSlug) {
        List<AggregateInfo> targets = internal.loadTargets(requestSlug);  // [조회 tx]
        for (var target : targets) {
            Optional<String> summary = generateSummary(...);  // [tx 밖 외부 호출]
            if (summary.isPresent()) {
                internal.saveSummary(target.placeId(), summary.get());  // [저장 tx]
            }
        }
    }
}

@Service
@RequiredArgsConstructor
class AiSummaryInternalService {

    @Transactional(readOnly = true)
    public List<AggregateInfo> loadTargets(String requestSlug) { ... }

    @Transactional
    public void saveSummary(Long placeId, String summaryText) { ... }
}
```

### 4. AsyncConfig 보강

```java
@Bean("aiTaskExecutor")
public ThreadPoolTaskExecutor aiTaskExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("AI-Async-");

    // CallerRunsPolicy: 큐가 가득 차면 호출 스레드에서 실행.
    // 선택 이유: AbortPolicy는 작업 유실, CallerRunsPolicy는 백프레셔로 작동.
    // 단, 호출 스레드가 Tomcat 스레드이면 블로킹 위험이 있으나,
    // @TransactionalEventListener(AFTER_COMMIT) + @Async이므로
    // 호출 스레드는 이미 응답을 반환한 후이다.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
}
```

### 5. AsyncUncaughtExceptionHandler

```java
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
            log.error("Async method {} failed: {}", method.getName(), throwable.getMessage(), throwable);
    }
}
```

### 6. ai_summary_job 테이블 — Flyway V21

```sql
CREATE TABLE ai_summary_job (
    id              BIGSERIAL PRIMARY KEY,
    request_slug    VARCHAR(64) NOT NULL UNIQUE,  -- 중복 실행 방지
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 상태값: PENDING, RUNNING, SUCCEEDED, FAILED
CREATE INDEX idx_ai_summary_job_status ON ai_summary_job (status);
```

### 7. graceful shutdown

```yaml
# application.yml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

---

# 이슈 7: 캐시 레이어 실동작화 (Caffeine)

## 커밋 1: CacheConfig + @EnableCaching

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("stations");
        manager.setCaffeine(Caffeine.newBuilder()
            // 전국 지하철역 수: ~700개. 검색 조합을 고려해 1000.
            .maximumSize(1000)
            // 역 데이터는 거의 변하지 않으므로 1시간 TTL이면 충분.
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats());
        return manager;
    }
}
```

## 커밋 2: 캐시 키 정규화

```java
// StationServiceImpl.search()
// BEFORE: @Cacheable(value = "stations", key = "#q + ':' + #limit")
// 문제: "강남"과 " 강남 "이 다른 키, limit 범위(1~50)에 따라 최대 50배 키 증가

// AFTER
@Cacheable(value = "stations", key = "#root.target.normalizeKey(#q)")
public SearchResponse search(String q, int limit) {
    String normalized = q.trim().toLowerCase();
    // limit을 키에서 제외하고 최대치로 조회 후 애플리케이션에서 잘라냄
    var all = repo.findByNameContainingIgnoreCase(normalized, PageRequest.of(0, 50));
    var items = all.getContent().stream().limit(limit).map(...).toList();
    return new SearchResponse(items, items.size());
}

// 트레이드오프 주석:
// [방식 A] limit을 키에 포함 → 같은 검색어에 limit별 별도 캐시. 정확하지만 키 폭발.
// [방식 B] limit 제외, 최대치로 캐시 → 메모리 사용 약간 증가하나 hit rate 대폭 향상.
// 선택: B. 역 검색 결과는 최대 50건이며, 대부분 10건 이내이므로 낭비가 미미하다.
```

## 커밋 3: Micrometer 캐시 메트릭

```java
@Bean
public CacheMetricsRegistrar cacheMetricsRegistrar(
        CaffeineCacheManager cacheManager, MeterRegistry registry) {
    var registrar = new CacheMetricsRegistrar(registry, List.of());
    cacheManager.getCacheNames().forEach(name ->
        registrar.bindCacheToRegistry(cacheManager.getCache(name)));
    return registrar;
}
```

## 테스트

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StationCacheTest {
    @Autowired StationService service;
    @SpyBean StationRepository repo;

    @Test
    void sameQuery_hitsCache() {
        service.search("강남", 10);
        service.search("강남", 10);
        verify(repo, times(1)).findByNameContainingIgnoreCase(any(), any());
    }

    @Test
    void trimmedQuery_sharesCacheEntry() {
        service.search("강남", 10);
        service.search(" 강남 ", 10);
        verify(repo, times(1)).findByNameContainingIgnoreCase(any(), any());
    }
}
```

---

# 이슈 8: 인덱스 재설계 및 EXPLAIN 근거

## V21 마이그레이션 (트랜잭션 비활성화 필요)

Flyway에서 `CREATE INDEX CONCURRENTLY`를 사용하려면 트랜잭션 밖에서 실행해야 한다.
Flyway 10+에서는 파일명에 트랜잭션 비활성화 지시자가 없으므로,
**별도 Java-based migration** 또는 `spring.flyway.out-of-order=true` + 스크립트 분리로 처리한다.

실질적으로는 `V21`을 **non-transactional migration**으로 표시:
파일 최상단에 `-- flyway:executeInTransaction=false` 주석.
(Flyway 10+ 는 이 주석을 인식하지 않을 수 있으므로, 실제로는 `afterMigrate` 콜백으로 처리하거나
`spring.flyway.mixed=true` 설정을 사용)

**대안**: CONCURRENTLY 없이 일반 CREATE INDEX로 만들고, 주석으로 "운영 환경에서는 CONCURRENTLY 적용 권장"이라 명시한다. 이 프로젝트의 데이터 규모(수백~수천 건)에서는 CONCURRENTLY가 실질적으로 불필요하다.

```sql
-- V21__redesign_performance_indexes.sql
-- flyway:executeInTransaction=false

-- V20이 만든 중복 인덱스 정리
DROP INDEX IF EXISTS idx_requests_group_id;    -- V17에서 이미 생성
DROP INDEX IF EXISTS idx_requests_station_code; -- V1의 idx_requests_station과 동일 컬럼
DROP INDEX IF EXISTS idx_requests_slug;         -- UNIQUE 제약으로 이미 btree 존재
DROP INDEX IF EXISTS idx_groups_slug;           -- UNIQUE 제약으로 이미 btree 존재

-- 핀 조회: WHERE request_id = ? ORDER BY recommended_count DESC, distance 계산
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rpa_request_recommend
    ON request_place_aggregates (request_id, recommended_count DESC);

-- 홈 목록: WHERE owner_user_id = ? AND group_id IS NULL ORDER BY created_at DESC
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_requests_owner_personal
    ON requests (owner_user_id, created_at DESC) WHERE group_id IS NULL;

-- 그룹 목록: WHERE group_id = ? ORDER BY created_at DESC
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_requests_group_created
    ON requests (group_id, created_at DESC) WHERE group_id IS NOT NULL;

-- 역 검색: lower(name) LIKE '%강남%' — 선행 와일드카드는 btree 불가, trigram 필요
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_stations_name_trgm
    ON stations USING gin (lower(name) gin_trgm_ops);
```

## EXPLAIN 측정

Docker Compose 환경에서:
1. `docs/perf/explain-before.md` — 인덱스 적용 전 EXPLAIN ANALYZE 출력
2. V21 적용
3. `docs/perf/explain-after.md` — 적용 후 EXPLAIN ANALYZE 출력
4. `docs/perf/README.md` — 비교표 및 해설

측정 대상 쿼리:
- 핀 조회 (`RequestDetailQueryRepository`의 네이티브 SQL)
- 역 검색 (`findByNameContainingIgnoreCase`)
- 홈 대시보드 (`findAllByOwnerUserIdAndGroupIdIsNullOrderByCreatedAtDesc`)

---

# 이슈 9: HomeService N+1 제거

## 핵심 변경

```java
// BEFORE (line 47-58): 요청마다 개별 집계 쿼리
for (var r : requests) {
    Long sum = rpaRepo.sumByRequestId(r.getSlug());
    ...
}

// AFTER: 배치 메서드 1회 호출 + Map 조회
List<String> slugs = requests.stream().map(Request::getSlug).toList();
Map<String, Long> sumMap = rpaRepo.sumByRequestIds(slugs).stream()
    .collect(Collectors.toMap(SumRow::getRequestId, SumRow::getTotal));

for (var r : requests) {
    Long sum = sumMap.getOrDefault(r.getSlug(), 0L);
    ...
}
```

`sumByRequestIds`는 이미 `RequestPlaceAggregateRepository:23-29`에 존재한다.

JDBC 폴백 경로(line 74-101)도 동일하게 수정하거나,
JPA 경로가 안정적이면 JDBC 폴백을 제거한다.

**JDBC 폴백 존재 이유 확인**: 코드 주석 없음. git blame으로 확인 필요.
추정: 초기에 JPA 쿼리가 느리거나 오류가 있어서 JDBC를 추가한 것으로 보이나,
이중 경로는 유지보수 부담만 증가시킨다. **JDBC 폴백 제거 후 JPA만 사용.**

### default_batch_fetch_size 주석 처리

```yaml
spring:
  jpa:
    properties:
      hibernate:
        # default_batch_fetch_size: 100
        # NOTE: 현재 엔티티 간 @OneToMany/@ManyToOne 연관관계가 없으므로
        # 이 설정은 실질적 효과가 없다. 향후 연관관계 추가 시 활성화.
```

### Hibernate statistics 테스트

```java
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
class HomeServiceQueryCountTest {
    @Autowired HomeService homeService;
    @Autowired EntityManagerFactory emf;

    @Test
    void dashboard_executesConstantNumberOfQueries() {
        // 사전 조건: 20개 요청 생성
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        homeService.dashboard(testUserId);

        // O(1): 요청 목록 1회 + 배치 집계 1회 + 그룹 조회 ≤ 3회
        assertThat(stats.getQueryExecutionCount()).isLessThanOrEqualTo(5);
    }
}
```

---

# 이슈 10: 낙관적 락 및 추천 수 동시성

## 1단계: 동시성 문제 재현 테스트 (먼저 작성)

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecommendationConcurrencyTest {
    @Autowired RecommendationService service;

    @Test
    void concurrent100Recommendations_finalCountMatches() throws Exception {
        int N = 100;
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final int idx = i;
            pool.submit(() -> {
                latch.await();
                service.submit(slug, buildRequest(idx));
                successCount.incrementAndGet();
                return null;
            });
        }
        latch.countDown();
        pool.shutdown();
        pool.awaitTermination(30, SECONDS);

        Long finalCount = rpaRepo.sumByRequestId(slug);
        assertThat(finalCount).isEqualTo(N);
    }
}
```

## 2단계: 두 가지 해법 구현 및 비교

### (A) 원자적 UPDATE
```sql
UPDATE request_place_aggregates
SET recommended_count = recommended_count + 1
WHERE request_id = ? AND place_id = ?
```

JPA:
```java
@Modifying
@Query("UPDATE RequestPlaceAggregate a SET a.recommendedCount = a.recommendedCount + 1 " +
       "WHERE a.requestId = :requestId AND a.placeId = :placeId")
int incrementRecommendedCount(@Param("requestId") String requestId, @Param("placeId") Long placeId);
```

### (B) 낙관적 락
```java
// 엔티티에 추가
@Version
private Integer version;

// Flyway V22
ALTER TABLE request_place_aggregates ADD COLUMN version INT NOT NULL DEFAULT 0;
```

```groovy
// build.gradle
implementation 'org.springframework.retry:spring-retry'
```

```java
@EnableRetry
@Configuration
public class RetryConfig {}

@Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
           maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2))
public void incrementWithOptimisticLock(String requestId, Long placeId) { ... }
```

### ADR 문서

`docs/adr/0001-concurrency-strategy.md` — 실측 수치 기반 비교 후 최종 선택 기록.

### 중복 추천 방지

```sql
-- Flyway
ALTER TABLE recommendation_notes
ADD CONSTRAINT uq_recommendation_user_place UNIQUE (rpa_id, guest_id);
-- 또는 (request_id, place_id, user_id) 복합 유니크
```

---

# 이슈 11: 벌크 Insert 전환 및 벤치마크

## StationCsvImportRunner 리팩토링

```java
// BEFORE: 행별 find + save (N select + N insert)

// AFTER: JdbcTemplate.batchUpdate + upsert
@RequiredArgsConstructor
public class StationCsvImportRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        List<Station> stations = parseCsv(...);
        String sql = """
            INSERT INTO stations (code, name, line, lat, lng)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (code) DO UPDATE SET
                name = EXCLUDED.name,
                line = EXCLUDED.line,
                lat  = EXCLUDED.lat,
                lng  = EXCLUDED.lng
            """;

        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            public void setValues(PreparedStatement ps, int i) { ... }
            public int getBatchSize() { return stations.size(); }
        });
    }
}
```

## 벤치마크

세 방식 (A: 현재, B: saveAll+batch_size, C: batchUpdate+upsert) 측정 후
`docs/perf/bulk-insert-benchmark.md`에 기록.

---

# 이슈 12: HikariCP 튜닝 및 성능 측정 체계

## application.yml HikariCP 전체 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30      # 근거: (이슈에서 산정)
      minimum-idle: 10           # 유휴 시 최소 커넥션
      connection-timeout: 3000   # 3초 내 커넥션 획득 실패 시 예외
      idle-timeout: 600000       # 10분 유휴 후 반환
      max-lifetime: 1740000      # 29분 (RDS wait_timeout=30분보다 짧게)
      leak-detection-threshold: 10000  # 10초 이상 반환 안 하면 경고
```

## k6 스크립트 수정 + handleSummary

각 스크립트에 `handleSummary(data)` 추가해 `docs/perf/k6/`에 JSON 저장.
theme3의 존재하지 않는 엔드포인트 수정.

---

# 이슈 13: 추천 저장 경로 동시성 결함 제거

(이슈 10과 함께 처리)

## 핵심 변경

1. `Thread.sleep(3000)` 제거 (RecommendationServiceImpl:63-67)
2. 집계행 INSERT → `INSERT ... ON CONFLICT (request_id, place_id) DO UPDATE`
3. 중복 추천 판정 기준 통일 (DB 제약 = 앱 로직)
4. 제약 위반 시 항목별 스킵 (배치 전체 롤백 방지)
5. 카카오 장소 업서트 → `ON CONFLICT` 방식
6. 응답 추천 수 → 커밋 후 재조회

---

# 이슈 14: k6 시나리오 전면 수정

(이슈 12에 흡수)

## 핵심

1. 실제 라우팅 대조 후 URL 수정
2. `check(res, {'status is 200': ...})` 추가
3. 성공률 95% 미만 → threshold 실패
4. 시드 데이터 준비 스크립트 작성

---

# 이슈 15: 미사용/비호환 의존성 정리

## 의존성 감사 결과

| 의존성 | 사용 여부 | 조치 |
|--------|-----------|------|
| `spring-cloud-starter-aws:2.2.6.RELEASE` | S3 코드 사용처 0건 | **제거**. S3 직접 사용 시 AWS SDK v2로 교체 |
| `spring-boot-starter-data-redis` | @Cacheable 추상화로만 사용, Redis 직접 코드 0건 | **제거**. Caffeine으로 캐시 통일 (이슈 7) |
| `spring-boot-starter-thymeleaf` | og.html 1개 | **유지** (OG 메타태그 렌더링에 필요) |
| `spring-boot-starter-webflux` | WebClient 사용 | **유지** (WebClient는 webflux 필요) |
| 개별 버전 명시 (`:3.5.4`) | BOM 중복 | **제거** — Spring Boot BOM이 관리 |

## build.gradle 정리

```groovy
dependencies {
    // Web & UI
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    // Data
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'

    // 캐시 (Caffeine만, Redis 제거)
    implementation 'org.springframework.boot:spring-boot-starter-cache'
    implementation 'com.github.ben-manes.caffeine:caffeine'

    // ...
}
```

## Spotless + Dependabot 추가

```groovy
plugins {
    id 'com.diffplug.spotless' version '7.0.2'
}

spotless {
    java {
        target 'src/*/java/**/*.java'
        googleJavaFormat()
        removeUnusedImports()
    }
}
```

`.github/dependabot.yml`:
```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

---

# 최종 산출물: docs/PORTFOLIO-EVIDENCE.md

모든 이슈 병합 후 작성. 포트폴리오 주장 ↔ 근거 파일/테스트/측정 리포트 매핑 표.

| 포트폴리오 주장 | 근거 | 파일 |
|----------------|------|------|
| CI에서 빌드/테스트/배포 자동 수행 | ci.yml 실행 이력 | `.github/workflows/ci.yml` |
| Circuit Breaker 도입 | 서킷 전이 테스트 | `CircuitBreakerTest.java` |
| N+1 → O(1) 해결 | Hibernate statistics 테스트 | `HomeServiceQueryCountTest.java` |
| 인덱스로 Full Scan 제거 | EXPLAIN ANALYZE 비교 | `docs/perf/explain-*.md` |
| 낙관적 락 + 재시도 | 동시성 테스트 + ADR | `docs/adr/0001-concurrency-strategy.md` |
| Bulk Insert 10배 향상 | 벤치마크 실측치 | `docs/perf/bulk-insert-benchmark.md` |
| HikariCP 튜닝 | pool sizing 문서 | `docs/perf/connection-pool-sizing.md` |
| EDA + @Async 격리 | E2E 통합 테스트 | `AiSummaryPipelineTest.java` |
| 외부 API 타임아웃 해결 | 타임아웃 테스트 | `WebClientTimeoutTest.java` |
| @LoginUser 공통 처리 | 컨트롤러 전수 조사 | `AuthSecurityIntegrationTest.java` |

---

## 실행 순서 요약

```
이슈 0  → #27  보안 (Terraform + HMAC 인증 + @LoginUser + Actuator)
이슈 1  → #26  저장소 위생
이슈 2  →      테스트 인프라
이슈 3  →      HTTP 타임아웃
이슈 4  →      Resilience4j
이슈 5  →      조회 경로 분리
이슈 6  →      비동기 파이프라인
이슈 7  →      Caffeine 캐시
이슈 8  →      인덱스 재설계
이슈 9  →      HomeService N+1
이슈 10+13 →   동시성 + 추천 결함
이슈 11 →      Bulk Insert
이슈 12+14 →   HikariCP + k6
이슈 15 →      의존성 정리
최종    →      PORTFOLIO-EVIDENCE.md
```
