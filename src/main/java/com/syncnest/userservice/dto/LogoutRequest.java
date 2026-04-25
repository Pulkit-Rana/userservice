package com.syncnest.userservice.dto;

import com.syncnest.userservice.validation.FieldConstraints;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LogoutRequest {

    @Size(max = FieldConstraints.REFRESH_TOKEN_RAW_MAX, message = "refreshToken exceeds maximum length")
    private String refreshToken;

    @Size(max = FieldConstraints.DEVICE_ID_MAX, message = "deviceId exceeds maximum length")
    private String deviceId;
}
