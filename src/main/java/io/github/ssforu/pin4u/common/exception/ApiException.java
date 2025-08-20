package io.github.ssforu.pin4u.common.exception;

import java.util.Map;

public class ApiException extends RuntimeException {
    private final ApiErrorCode code;
    private final Map<String, Object> details;

    public ApiException(ApiErrorCode code, String message) {
        super(message);
        this.code = code;
        this.details = null;
    }

    public ApiException(ApiErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ApiErrorCode getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }
}
