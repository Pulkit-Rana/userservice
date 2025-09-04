package com.syncnest.userservice.controller;

import com.syncnest.userservice.dto.LoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.dto.LogoutRequest;
import com.syncnest.userservice.dto.LogoutResponse;
import com.syncnest.userservice.dto.RefreshTokenRequest;
import com.syncnest.userservice.dto.RefreshTokenResponse;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();
        response.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(response);
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = refreshTokenService.validateAndRotate(request);

        long maxAge = 0;
        if (response.getExpiresAt() != null) {
            long seconds = Instant.now().until(response.getExpiresAt(), ChronoUnit.SECONDS);
            maxAge = Math.max(seconds, 0);
        }

        // Set new refresh token in HttpOnly cookie and scrub body
        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofSeconds(maxAge))
                .build();

        response.setRefreshToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@Valid @RequestBody LogoutRequest request) {
        // Best-effort logout: revoke server-side refresh tokens if we can identify the user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null) ? auth.getName() : null;

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user -> {
                if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
                    refreshTokenService.revokeAllForUserDevice(user, request.getDeviceId());
                    log.info("Revoked refresh tokens for user={} deviceId={}", email, request.getDeviceId());
                } else {
                    refreshTokenService.revokeAllForUser(user);
                    log.info("Revoked all refresh tokens for user={}", email);
                }
            });
        }

        // Clear the refresh token cookie on the client
        ResponseCookie clear = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        LogoutResponse body = new LogoutResponse();
        body.setSuccess(true);
        body.setMessage("Logged out");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .body(body);
    }
}
