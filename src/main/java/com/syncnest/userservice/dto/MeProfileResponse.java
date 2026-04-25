package com.syncnest.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Authenticated user's profile for GET /users/me and PUT /users/me/profile.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeProfileResponse {

    private String id;
    private String email;
    private String displayName;
    private String profilePictureUrl;
    private Set<String> roles;
    private boolean emailVerified;
    /** True when this account is linked to Google (sign-in or linked). */
    private boolean googleLinked;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String city;
    private String country;
}
