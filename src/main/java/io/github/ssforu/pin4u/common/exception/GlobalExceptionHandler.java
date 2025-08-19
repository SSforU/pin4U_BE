package io.github.ssforu.pin4u.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String,Object>> handleApi(ApiException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UPSTREAM_RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case UPSTREAM_ERROR -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", ex.getCode().name());
        error.put("message", ex.getMessage());
        error.put("details", ex.getDetails());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", "error");
        body.put("error", error);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleAny(Exception ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", ApiErrorCode.INTERNAL_ERROR.name());
        error.put("message", "unexpected server error");
        error.put("details", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", "error");
        body.put("error", error);
        body.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
