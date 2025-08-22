package com.syncnest.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    private String accessToken;
    private long expiresIn;
    private String refreshToken;
    private String deviceId;
    private Instant issuedAt;
    private UserSummary user;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserSummary {
        private String id;
        private String email;
        private String displayName;
        private Set<String> roles;
        private boolean emailVerified;
    }
}
