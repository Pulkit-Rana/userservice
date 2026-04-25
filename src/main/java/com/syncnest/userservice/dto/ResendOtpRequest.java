package com.syncnest.userservice.dto;

import com.syncnest.userservice.validation.FieldConstraints;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResendOtpRequest {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Size(max = FieldConstraints.USER_EMAIL_MAX, message = "Email exceeds maximum length allowed for this service")
    private String email;
}

