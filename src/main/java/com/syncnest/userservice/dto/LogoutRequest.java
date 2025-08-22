package com.syncnest.userservice.dto;

import lombok.Data;

@Data
public class LogoutRequest {
    private String refreshToken;
    private String deviceId;
}
