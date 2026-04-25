package com.syncnest.userservice.dto;

import com.syncnest.userservice.validation.FieldConstraints;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = FieldConstraints.PROFILE_NAME_MAX, message = "First name exceeds maximum length")
    private String firstName;

    @Size(max = FieldConstraints.PROFILE_NAME_MAX, message = "Last name exceeds maximum length")
    private String lastName;
}
