package com.syncnest.userservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Browser CORS allowed origin patterns (not wildcards on scheme — use Spring patterns like {@code https://*.example.com}).
 */
@ConfigurationProperties(prefix = "app.cors")
@Getter
@Setter
public class CorsAppProperties {

    /**
     * When empty after binding, {@link com.syncnest.userservice.SecurityConfig.SecurityConfig} applies local defaults.
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*"
    ));
}
