package com.syncnest.userservice.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String timestamp,
        String requestId,
        String message,
        T data,
        Object meta
) {
    public static <T> ApiResponse<T> of(String requestId, String message, T data, Object meta) {
        return new ApiResponse<>(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                requestId,
                message,
                data,
                meta
        );
    }
    public static <T> ApiResponse<T> of(String requestId, String message, T data) {
        return of(requestId, message, data, null);
    }
}
