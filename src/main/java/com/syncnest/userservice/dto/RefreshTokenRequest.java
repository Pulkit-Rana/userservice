package com.syncnest.userservice.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {

    private String refreshToken;   // raw token from client
    private String deviceId;
}
