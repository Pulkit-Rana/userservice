package com.syncnest.userservice.dto;

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

    @Size(max = 64, message = "clientId must be <= 64 characters")
    private String clientId;

    @Size(max = 64, message = "deviceId must be <= 64 characters")
    private String deviceId;
}

