package com.syncnest.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OAuth2ExchangeRequest {

    /**
     * One-time ticket from the OAuth success redirect ({@code ?ott=...}), not Google's authorization code.
     */
    @NotBlank(message = "ott is required")
    @Size(max = 128)
    private String ott;
}
