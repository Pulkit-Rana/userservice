package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.dto.LoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProviderConfig jwtService;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) throws BadCredentialsException {
        final String email = normalizeEmail(request.getEmail());

        // 1) Authenticate (delegates to DaoAuthenticationProvider + PasswordEncoder)
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            // Keep message generic to prevent user enumeration
            log.debug("Authentication failed for {}: {}", email, ex.getMessage());
            throw new BadCredentialsException("Invalid email or password");
        }

        // 2) Load user and enforce account state (generic error on bad state)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.isDeleted() || user.isLocked() || !user.isEnabled() || !user.isVerified()) {
            // Do not leak which flag caused the failure
            throw new BadCredentialsException("Invalid email or password");
        }

        // 3) Update last login (UTC recommended)
        user.setLastLoginAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()));

        if (user.getProvider() == null) {
            user.setProvider(com.syncnest.userservice.entity.AuthProvider.LOCAL);
        }
        // NOTE: Do NOT set provider=LOCAL if user.provider is GOOGLE/APPLE; that user is meant for OAuth-only.

        // 5) Persist the updates
        userRepository.saveAndFlush(user);

        HashMap<String, Object> claims = new HashMap<>();
        claims.put("uid", safeId(user.getId()));
        claims.put("role", user.getRole().name());

        String accessToken = jwtService.generateToken(claims, email);

        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(accessToken);
        resp.setExpiresIn(jwtService.getTokenValiditySeconds());
        resp.setIssuedAt(Instant.now());

        LoginResponse.UserSummary summary = new LoginResponse.UserSummary();
        summary.setId(safeId(user.getId()));
        summary.setEmail(user.getEmail());
        summary.setDisplayName(user.getEmail());
        summary.setRoles(Set.of(user.getRole().name()));
        summary.setEmailVerified(user.isVerified());

        resp.setUser(summary);
        return resp;
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new BadCredentialsException("Invalid email or password");
        return email.trim().toLowerCase();
    }

    private String safeId(UUID id) {
        return id != null ? id.toString() : null;
    }
}
