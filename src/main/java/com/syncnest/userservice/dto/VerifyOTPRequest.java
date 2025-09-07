package com.syncnest.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class VerifyOTPRequest {

    @Email
    @NotBlank
    private String email;
    @NotBlank
    private String otp;
}
