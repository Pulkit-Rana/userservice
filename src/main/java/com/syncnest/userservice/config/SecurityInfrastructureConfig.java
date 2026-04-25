package com.syncnest.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password and {@link AuthenticationManager} beans live here (not on {@code SecurityConfig}) so that
 * {@code SecurityConfig} can constructor-inject OAuth2 handlers without a cycle:
 * {@code SecurityConfig} → handlers → {@code GoogleAuthServiceImpl} → {@code PasswordEncoder}.
 */
@Configuration
public class SecurityInfrastructureConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
