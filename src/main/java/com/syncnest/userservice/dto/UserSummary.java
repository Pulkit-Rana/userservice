package com.syncnest.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSummary {
    private String id;
    private String email;
    private String displayName;
    /** From profile when set (e.g. Google picture URL). */
    private String profilePictureUrl;
    private Set<String> roles;
    private boolean emailVerified;
    /** True when Google sign-in is linked (helps clients show profile / password hints). */
    private boolean googleLinked;
}