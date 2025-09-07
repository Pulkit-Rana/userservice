package com.syncnest.userservice.controller;

import com.syncnest.userservice.dto.*;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.OtpService;
import com.syncnest.userservice.service.RefreshTokenService;
import com.syncnest.userservice.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String NEXT_SECURED_PATH = "/dashboard";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final RegistrationService registrationService;
    private final OtpService otpService;

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

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

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        // 1) Persist user (locked, not verified, disabled)
        RegistrationResponse persisted = registrationService.registerUser(request);
        String email = persisted.getEmail();

        // 2) Generate + send OTP (rate-limited by your otpService)
        otpService.generateAndSendOtp(email);

        OtpStatus status = otpService.getOtpStatus(email);

        OtpMeta otpMeta = OtpMeta.builder()
                .used(status.used())                       // includes current send
                .max(status.max())
                .cooldown(status.cooldown())
                .resendIntervalLock(status.resendIntervalLock())
                .resendIntervalSeconds(status.resendIntervalSeconds())
                .cooldownSeconds(status.cooldownSeconds())
                .build();

        // 3) Build response compatible with the provided RegistrationResponse DTO
        RegistrationResponse body = RegistrationResponse.builder()
                .success(true)
                .message("Registration initiated. An OTP has been sent to your email. Verify within 1 minute.")
                .email(email)
                .otpMeta(otpMeta)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, "/api/v1/auth/verify-otp");
        return new ResponseEntity<>(body, headers, HttpStatus.CREATED);
    }

    @PostMapping("/verify-otp")
    @Transactional
    public ResponseEntity<LoginResponse> verifyOtp(@RequestBody @Valid VerifyOTPRequest verifyOtp) {
        // 1) Verify OTP and issue tokens (service returns a full LoginResponse)
        LoginResponse response = otpService.verifyAndConsumeOtpOrThrow(verifyOtp);

        // 2) Mark account state; will be persisted by @Transactional
        User user = userRepository.findByEmail(verifyOtp.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found for email."));
        user.setVerified(true);
        user.setEnabled(true);
        user.setLocked(false);

        // 3) Move refresh token into HttpOnly cookie, strip from JSON body
        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();
        response.setRefreshToken(null); // do not leak in JSON

        // 4) Return
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .header(HttpHeaders.LOCATION, NEXT_SECURED_PATH) // optional hint
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
