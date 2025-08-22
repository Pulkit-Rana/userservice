package com.syncnest.userservice.dto;

import lombok.Data;

@Data
public class LogoutResponse {
    private boolean success;
    private String message;
}
