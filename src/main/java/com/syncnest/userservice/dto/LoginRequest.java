package com.syncnest.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginRequest {

    @NotBlank(message = "email is required")
    @Size(max = 320, message = "email must be <= 320 characters")
    @jakarta.validation.constraints.Email(message = "email is invalid")
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 256, message = "password length must be between 8 and 256")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Size(max = 64, message = "clientId must be <= 64 characters")
    private String clientId;

    @Size(max = 64, message = "deviceId must be <= 64 characters")
    private String deviceId;

    @Size(max = 45, message = "ip must be <= 45 characters")
    private String ip;

    @Size(max = 255, message = "userAgent must be <= 255 characters")
    private String userAgent;
}
