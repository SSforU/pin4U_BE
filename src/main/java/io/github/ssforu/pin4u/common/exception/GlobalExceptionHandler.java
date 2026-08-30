package io.github.ssforu.pin4u.common.exception;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        ApiErrorCode code = ex.getCode();
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code.name(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage())
                        .findFirst()
                        .orElse("validation error");
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", msg, null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", ex.getParameterName() + " is required", null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String msg =
                ex.getConstraintViolations().stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .findFirst()
                        .orElse("constraint violation");
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", msg, null));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex, HttpServletRequest req) {
        if (log.isDebugEnabled()) {
            log.debug("[NOT_FOUND] {} {}", req.getMethod(), req.getRequestURI(), ex);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "not found", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        if (log.isDebugEnabled()) {
            log.debug("[METHOD_NOT_ALLOWED] {} {}", req.getMethod(), req.getRequestURI(), ex);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("METHOD_NOT_ALLOWED", "method not allowed", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleRse(ResponseStatusException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String code =
                switch (status) {
                    case BAD_REQUEST -> "BAD_REQUEST";
                    case NOT_FOUND -> "NOT_FOUND";
                    case UNAUTHORIZED -> "UNAUTHORIZED";
                    case FORBIDDEN -> "FORBIDDEN";
                    case TOO_MANY_REQUESTS -> "RATE_LIMITED";
                    default -> "ERROR";
                };
        return ResponseEntity.status(status)
                .body(
                        ApiResponse.error(
                                code, ex.getReason() != null ? ex.getReason() : "error", null));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebClient(
            WebClientResponseException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        String code;
        if (status.is5xxServerError()) {
            code = "UPSTREAM_ERROR";
        } else if (status == HttpStatus.UNAUTHORIZED) {
            code = "UNAUTHORIZED";
        } else {
            code = "BAD_REQUEST";
        }

        if (status.is5xxServerError()) {
            log.warn("[UPSTREAM] {} {} - {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        } else if (log.isDebugEnabled()) {
            log.debug(
                    "[WEBCLIENT] {} {} - {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        }
        return ResponseEntity.status(status)
                .body(ApiResponse.error(code, ex.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String msg = ex.getMessage();
        HttpStatus status;
        String code;

        if ("invalid_kakao_token".equals(msg) || "kakao_me_null".equals(msg)) {
            status = HttpStatus.UNAUTHORIZED;
            code = "UNAUTHORIZED";
        } else {
            status = HttpStatus.BAD_REQUEST;
            code = "BAD_REQUEST";
        }

        return ResponseEntity.status(status).body(ApiResponse.error(code, msg, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        log.error(
                "[UNEXPECTED] {} {} - {}",
                req.getMethod(),
                req.getRequestURI(),
                ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "unexpected server error", null));
    }
}
