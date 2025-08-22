package com.syncnest.userservice.dto; // <- apne project ke hisaab se adjust karo

import lombok.*;
import java.time.Instant;

/**
 * Generic API envelope.
 * Supports:
 *  - ApiResponse.of(String code, String message, Object data, Object meta)
 *  - ApiResponse.<T>builder().success(...).code(...).message(...).data(...).meta(...).build()
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ApiResponse<T> {

    /**
     * Whether the operation was successful.
     * This field name MUST be 'success' for builder.success(...) to exist.
     */
    @Builder.Default
    private boolean success = true;

    /** Application-level code, e.g. "OK", "UNAUTHORIZED", "VALIDATION_ERROR" */
    private String code;

    /** Human-readable message */
    private String message;

    /** Payload */
    private T data;

    /** Optional metadata (pagination, trace, etc.) */
    private Object meta;

    /** Server-side timestamp */
    @Builder.Default
    private Instant timestamp = Instant.now();

    // ---------------- Static factories to satisfy your existing calls ----------------

    /** Matches: ApiResponse.of(code, message, data, meta) */
    public static ApiResponse<Object> of(String code, String message, Object data, Object meta) {
        return ApiResponse.builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .meta(meta)
                .build();
    }

    /** Convenience: success with only code/message */
    public static ApiResponse<Object> of(String code, String message) {
        return ApiResponse.builder()
                .success(true)
                .code(code)
                .message(message)
                .build();
    }

    /** Convenience: error */
    public static ApiResponse<Object> error(String code, String message) {
        return ApiResponse.builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }

    /** Convenience: OK with data */
    public static <U> ApiResponse<U> ok(U data) {
        return ApiResponse.<U>builder()
                .success(true)
                .code("OK")
                .message("success")
                .data(data)
                .build();
    }
}
