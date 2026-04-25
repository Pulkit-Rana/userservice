package com.syncnest.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuth2ExchangeResponse {

    private String accessToken;
    private long expiresIn;
    private UserSummary user;
}
