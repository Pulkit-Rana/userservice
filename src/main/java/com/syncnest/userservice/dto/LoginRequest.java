package com.syncnest.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceType;
import com.syncnest.userservice.validation.FieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginRequest {

    @NotBlank(message = "email is required")
    @Size(max = FieldConstraints.USER_EMAIL_MAX, message = "email exceeds maximum length allowed for this service")
    @jakarta.validation.constraints.Email(message = "email is invalid")
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = FieldConstraints.PASSWORD_MAX, message = "password length is outside allowed bounds")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Size(max = FieldConstraints.DEVICE_ID_MAX, message = "clientId exceeds maximum length")
    private String clientId;

    @Size(max = FieldConstraints.DEVICE_ID_MAX, message = "deviceId exceeds maximum length")
    private String deviceId;

    @Size(max = 45, message = "ip must be <= 45 characters")
    private String ip;

    @Size(max = FieldConstraints.DEVICE_USER_AGENT_MAX, message = "userAgent exceeds maximum length")
    private String userAgent;

    // New fields for your recordDeviceLogin method
    private AuthProvider provider = AuthProvider.LOCAL; // default
    private DeviceType deviceType = DeviceType.UNKNOWN; // default

    @Size(max = FieldConstraints.DEVICE_LOCATION_MAX, message = "location exceeds maximum length")
    private String location;
}
