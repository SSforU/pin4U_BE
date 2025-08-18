package io.github.ssforu.pin4u.common.response;

// 필수 - 표준 응답 래퍼 임시로 채워놓음
public class ApiResponse<T> {
    private final String result;
    private final T data;

    private ApiResponse(String result, T data) {
        this.result = result;
        this.data = data;
    }
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>("success", data); }
    public String getResult() { return result; }
    public T getData() { return data; }
}
