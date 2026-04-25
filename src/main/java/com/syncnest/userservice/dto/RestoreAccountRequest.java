package com.syncnest.userservice.dto;

import com.syncnest.userservice.validation.FieldConstraints;
import jakarta.validation.constraints.Email;
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
public class RestoreAccountRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max = FieldConstraints.USER_EMAIL_MAX, message = "Email exceeds maximum length allowed for this service")
    private String email;

    @NotBlank(message = "Password is required to verify identity")
    @Size(min = 8, max = FieldConstraints.PASSWORD_MAX, message = "Password length is outside allowed bounds")
    private String password;
}

