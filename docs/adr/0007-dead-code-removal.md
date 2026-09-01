# ADR-0007: 데드 코드 제거

- 상태: 수용
- 일시: 2026-09-01

## 맥락

코드베이스 전수 점검에서 등록은 되지만 사용되지 않는 Bean 3개를 발견했다.
데드 Bean은 앱 기동 시 불필요한 초기화를 유발하고, 의존성 변경 시 빌드 오류의
원인이 될 수 있다.

## 제거 대상

| 클래스 | 이유 |
|--------|------|
| `AppProperties` | `@ConfigurationProperties(prefix="app")` 바인딩 클래스이나 전체 코드에서 주입되지 않음. 모든 설정값은 `@Value`로 직접 주입 |
| `MockAllocator` | place_mock 일괄 생성 서비스이나 어떤 컨트롤러/서비스에서도 주입하지 않음 |
| `MockDataGenerator` | `MockAllocator`에서만 참조. MockAllocator 제거 시 함께 데드 |

## 결정

3개 클래스를 삭제한다. 삭제 후 전체 빌드 + 29건 단위 테스트 통과를 확인했다.

## 남겨둔 것

- `PlaceMock` 엔티티와 `PlaceMockRepository`는 `PlaceSearchServiceImpl`에서 사용 중이므로 유지
- `NoopKakaoSearchAdapter`는 `@ConditionalOnMissingBean` 패턴으로 정상 동작
