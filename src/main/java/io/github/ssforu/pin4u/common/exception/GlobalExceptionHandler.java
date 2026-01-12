// src/main/java/io/github/ssforu/pin4u/common/exception/GlobalExceptionHandler.java
package io.github.ssforu.pin4u.common.exception;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // ✅ Lombok 미의존: 직접 로거 선언
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 400 - @Valid 본문 유효성
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("validation error");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", msg, null));
    }

    // 400 - @Validated 파라미터 유효성
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("constraint violation");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", msg, null));
    }

    // 404 - 리소스/핸들러 없음 (정적/동적 모두 커버)
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex, HttpServletRequest req) {
        if (log.isDebugEnabled()) {
            log.debug("[NOT_FOUND] {} {}", req.getMethod(), req.getRequestURI(), ex);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "not found", null));
    }

    // 405 - 메서드 불일치
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                      HttpServletRequest req) {
        if (log.isDebugEnabled()) {
            log.debug("[METHOD_NOT_ALLOWED] {} {}", req.getMethod(), req.getRequestURI(), ex);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("METHOD_NOT_ALLOWED", "method not allowed", null));
    }

    // ResponseStatusException → 해당 상태 그대로 전달
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleRse(ResponseStatusException ex) {
        HttpStatus status = (HttpStatus) ex.getStatusCode();
        String code = switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case NOT_FOUND -> "NOT_FOUND";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            default -> "ERROR";
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.error(code, ex.getReason() != null ? ex.getReason() : "error", null));
    }

    // ★ 카카오/외부 호출 실패(WebClientResponseException) 전용 매핑
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebClient(WebClientResponseException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.BAD_GATEWAY;

        String code;
        if (status.is5xxServerError()) {
            code = "UPSTREAM_ERROR";        // 5xx → 502 등
        } else if (status == HttpStatus.UNAUTHORIZED) {
            code = "UNAUTHORIZED";          // 401
        } else {
            code = "BAD_REQUEST";           // 기타 4xx
        }

        if (status.is5xxServerError()) {
            log.warn("[UPSTREAM] {} {} - {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[WEBCLIENT] {} {} - {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
            }
        }
        return ResponseEntity.status(status)
                .body(ApiResponse.error(code, ex.getMessage(), null));
    }

    // IllegalArgumentException → 메시지 기반 상태코드 분기(유지 + 보강)
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage();
        HttpStatus status;
        String code;

        // ★ 의미 있는 메시지에 상태코드 부여
        if ("invalid_kakao_token".equals(msg)) {
            status = HttpStatus.UNAUTHORIZED; // 401
            code = "UNAUTHORIZED";
        } else if ("kakao_4xx".equals(msg)
                || "access_token_required".equals(msg)
                || "nickname_required".equals(msg)
                || "nickname_length_2_to_16".equals(msg)) {
            status = HttpStatus.BAD_REQUEST;  // 400
            code = "BAD_REQUEST";
        } else {
            status = HttpStatus.BAD_REQUEST;
            code = "BAD_REQUEST";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", "error");
        body.put("data", null);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", msg);
        err.put("details", null);
        body.put("error", err);
        body.put("timestamp", java.time.OffsetDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    // 그 외 예기치 못한 예외만 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("[UNEXPECTED] {} {} - {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "unexpected server error", null));
    }
}
