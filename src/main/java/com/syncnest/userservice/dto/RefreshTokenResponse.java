package com.syncnest.userservice.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class RefreshTokenResponse {

    private String refreshToken;  // raw token to be stored by client
    private Instant expiresAt;    // when this token will expire
    private String deviceId;
}
