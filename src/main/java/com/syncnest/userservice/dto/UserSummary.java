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
    private Set<String> roles;
    private boolean emailVerified;
}