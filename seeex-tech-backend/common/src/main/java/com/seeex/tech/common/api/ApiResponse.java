package com.seeex.tech.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse<T> {
    private final T data;
    private final String code;
    private final String message;
    private final ErrorType errorType;

    private ApiResponse(T data, String code, String message, ErrorType errorType) {
        this.data = data;
        this.code = code;
        this.message = message;
        this.errorType = errorType;
    }

    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(data, "OK", "success", null); }
    public static <T> ApiResponse<T> fail(String code, ErrorType type, String message) { return new ApiResponse<>(null, code, message, type); }
    public T getData() { return data; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    @JsonProperty("error_type")
    public ErrorType getErrorType() { return errorType; }
}
