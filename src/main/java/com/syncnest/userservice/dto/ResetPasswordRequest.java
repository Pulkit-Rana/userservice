package com.syncnest.userservice.dto;

import com.syncnest.userservice.Validators.PasswordMatch;
import com.syncnest.userservice.validation.FieldConstraints;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PasswordMatch(passwordField = "newPassword", passwordConfirmationField = "passwordConfirmation")
public class ResetPasswordRequest {

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email is not valid")
    @Size(max = FieldConstraints.USER_EMAIL_MAX, message = "Email exceeds maximum length allowed for this service")
    private String email;

    @NotBlank(message = "Reset code is required")
    @Pattern(regexp = "^\\d{6}$", message = "Reset code must be 6 digits")
    private String resetCode;

    @NotBlank(message = "New password cannot be empty")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).*$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character."
    )
    @Size(min = 8, max = FieldConstraints.PASSWORD_MAX, message = "New password length is outside allowed bounds")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    @Size(max = FieldConstraints.PASSWORD_MAX, message = "Password confirmation exceeds maximum length")
    private String passwordConfirmation;
}

