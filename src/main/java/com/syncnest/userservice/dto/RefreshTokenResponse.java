package com.syncnest.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefreshTokenResponse {

    private String accessToken;       // new JWT access token (issued on rotation)
    private long expiresIn;           // access token validity in seconds
    private String refreshToken;      // raw refresh token to be stored by client
    private Instant expiresAt;        // when the refresh token will expire
    private String deviceId;
}
