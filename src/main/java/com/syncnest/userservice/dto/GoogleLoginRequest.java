package com.syncnest.userservice.dto;

import com.syncnest.userservice.validation.FieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;

    @Size(max = FieldConstraints.DEVICE_ID_MAX, message = "clientId exceeds maximum length")
    private String clientId;

    @Size(max = FieldConstraints.DEVICE_ID_MAX, message = "deviceId exceeds maximum length")
    private String deviceId;
}

