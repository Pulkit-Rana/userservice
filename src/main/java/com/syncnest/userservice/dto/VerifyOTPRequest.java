package com.syncnest.userservice.dto;

import com.syncnest.userservice.validation.FieldConstraints;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class VerifyOTPRequest {

    @Email
    @NotBlank
    @Size(max = FieldConstraints.USER_EMAIL_MAX, message = "Email exceeds maximum length allowed for this service")
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code from your email.")
    private String otp;
}
