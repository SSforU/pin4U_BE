# ADR-0006: 예외 처리 정규화

- 상태: 수용
- 일시: 2026-08-30

## 맥락

`ApiException`이 비즈니스 코드에서 사용되지만 `GlobalExceptionHandler`에
핸들러가 없어 catch-all(`Exception`)에 잡히면서 500을 반환했다.
`IllegalArgumentException` 핸들러는 `ApiResponse`를 사용하지 않고
`Map<String,Object>`를 수동 생성하여 응답 형식이 불일치했다.

## 결정

1. **`ApiException` 핸들러 추가**: `ApiErrorCode`의 HTTP 상태를 사용해 응답
2. **`ApiErrorCode`에 HTTP 상태 매핑**: 각 에러 코드가 자신의 상태를 알도록 수정
3. **`IllegalArgumentException` 핸들러 통일**: `Map` 수동 생성 → `ApiResponse.error()` 사용
4. **`handleRse` 캐스팅 안전화**: `(HttpStatus) ex.getStatusCode()` → `HttpStatus.resolve()`
5. **`@ResponseBody` 제거**: `@RestControllerAdvice`가 이미 포함하므로 불필요

## 결과

- 모든 예외 응답이 `ApiResponse` 형식으로 통일
- `ApiException` → 적절한 상태코드(400, 404, 502 등) 반환
- 비표준 HTTP 상태코드에서 ClassCastException 발생 불가
