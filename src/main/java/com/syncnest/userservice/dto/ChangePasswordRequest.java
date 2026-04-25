package com.syncnest.userservice.dto;

import com.syncnest.userservice.Validators.PasswordMatch;
import com.syncnest.userservice.validation.FieldConstraints;
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
@PasswordMatch(passwordField = "newPassword", passwordConfirmationField = "confirmPassword")
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Size(max = FieldConstraints.PASSWORD_MAX, message = "Current password exceeds maximum length")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).*$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character."
    )
    @Size(min = 8, max = FieldConstraints.PASSWORD_MAX, message = "New password length is outside allowed bounds")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    @Size(max = FieldConstraints.PASSWORD_MAX, message = "Password confirmation exceeds maximum length")
    private String confirmPassword;
}
