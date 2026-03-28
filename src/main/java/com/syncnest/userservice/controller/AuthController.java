package com.syncnest.userservice.controller;

import com.syncnest.userservice.dto.*;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.OtpService;
import com.syncnest.userservice.service.PasswordResetService;
import com.syncnest.userservice.service.RefreshTokenService;
import com.syncnest.userservice.service.RegistrationService;
import com.syncnest.userservice.service.UserAccountService;
import com.syncnest.userservice.utils.RequestMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    private static final String NEXT_SECURED_PATH = "/dashboard";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final RegistrationService registrationService;
    private final OtpService otpService;
    private final PasswordResetService passwordResetService;
    private final UserAccountService userAccountService;
    private final RequestMetadataExtractor metadataExtractor;

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.debug("Login request received from IP: {}, UserAgent: {}", 
                metadataExtractor.extractIp(httpRequest),
                metadataExtractor.extractUserAgent(httpRequest));

        // Always override IP and User-Agent from real HTTP headers — never trust the request body
        request.setIp(metadataExtractor.extractIp(httpRequest));
        request.setUserAgent(metadataExtractor.extractUserAgent(httpRequest));
        request.setDeviceType(metadataExtractor.parseDeviceType(request.getUserAgent()));

        LoginResponse response = authService.login(request);

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(false)   // set true in production (HTTPS)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();
        response.setRefreshToken(null);

        log.info("Login response prepared for email: {}, accessToken issued", maskEmail(response.getUser().getEmail()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(response);
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        log.debug("Refresh token request received, deviceId: {}", request.getDeviceId());

        RefreshTokenResponse response = refreshTokenService.validateAndRotate(request);

        long maxAge = 0;
        if (response.getExpiresAt() != null) {
            long seconds = Instant.now().until(response.getExpiresAt(), ChronoUnit.SECONDS);
            maxAge = Math.max(seconds, 0);
        }

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofSeconds(maxAge))
                .build();

        response.setRefreshToken(null);
        log.info("Refresh token rotated successfully, new expiry: {}", response.getExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(response);
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request) {

        log.debug("Registration request received for email: {}", maskEmail(request.getEmail()));

        RegistrationResponse persisted = registrationService.registerUser(request);
        String email = persisted.getEmail();

        otpService.generateAndSendOtp(email);
        OtpStatus status = otpService.getOtpStatus(email);

        OtpMeta otpMeta = OtpMeta.builder()
                .used(status.used())
                .max(status.max())
                .cooldown(status.cooldown())
                .resendIntervalLock(status.resendIntervalLock())
                .resendIntervalSeconds(status.resendIntervalSeconds())
                .cooldownSeconds(status.cooldownSeconds())
                .build();

        RegistrationResponse body = RegistrationResponse.builder()
                .success(true)
                .message(persisted.getMessage())
                .email(email)
                .otpMeta(otpMeta)
                .build();

        log.info("Registration initiated for email: {}, OTP sent", maskEmail(email));
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, "/api/v1/auth/verify-otp");
        return new ResponseEntity<>(body, headers, HttpStatus.CREATED);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse> verifyOtp(
            @RequestBody @Valid VerifyOTPRequest verifyOtp,
            HttpServletRequest httpRequest) {

        log.debug("OTP verification request received for email: {}", maskEmail(verifyOtp.getEmail()));

        // Build a real-time DeviceContext from the HTTP request so device tracking
        // captures actual IP/UA even on first login (no deviceId available yet).
        String ip = metadataExtractor.extractIp(httpRequest);
        String ua = metadataExtractor.extractUserAgent(httpRequest);
        DeviceContext deviceContext = DeviceContext.builder()
                .ip(ip)
                .userAgent(ua)
                .os(metadataExtractor.parseOs(ua))
                .browser(metadataExtractor.parseBrowser(ua))
                .deviceType(metadataExtractor.parseDeviceType(ua))
                .provider(AuthProvider.LOCAL)
                .build();

        LoginResponse response = otpService.verifyAndConsumeOtpOrThrow(verifyOtp, deviceContext);

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();

        response.setRefreshToken(null);

        log.info("OTP verification successful, user logged in: {}", maskEmail(response.getUser().getEmail()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .header(HttpHeaders.LOCATION, NEXT_SECURED_PATH)
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@Valid @RequestBody LogoutRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null) ? auth.getName() : null;

        log.debug("Logout request received from authenticated user: {}", maskEmail(email));

        if (email != null) {
            userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(user -> {
                if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
                    refreshTokenService.revokeAllForUserDevice(user, request.getDeviceId());
                    log.info("Revoked refresh tokens for user={} deviceId={}", maskEmail(email), request.getDeviceId());
                } else {
                    refreshTokenService.revokeAllForUser(user);
                    log.info("Revoked all refresh tokens for user={}", maskEmail(email));
                }
            });
        }

        ResponseCookie clear = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        LogoutResponse body = new LogoutResponse();
        body.setSuccess(true);
        body.setMessage("Logged out");

        log.info("Logout successful for user: {}", maskEmail(email));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .body(body);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordActionResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        String ip = metadataExtractor.extractIp(httpRequest);
        String userAgent = metadataExtractor.extractUserAgent(httpRequest);

        passwordResetService.sendResetCode(request.getEmail(), ip, userAgent);

        // Keep response generic to avoid account enumeration.
        return ResponseEntity.ok(PasswordActionResponse.builder()
                .success(true)
                .message("If the account exists, a reset code has been sent to your email.")
                .build());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<PasswordActionResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String ip = metadataExtractor.extractIp(httpRequest);
        String userAgent = metadataExtractor.extractUserAgent(httpRequest);

        passwordResetService.resetPassword(
                request.getEmail(),
                request.getResetCode(),
                request.getNewPassword(),
                ip,
                userAgent
        );

        return ResponseEntity.ok(PasswordActionResponse.builder()
                .success(true)
                .message("Password reset successful. Please log in with your new password.")
                .build());
    }

    @PostMapping("/restore-account")
    public ResponseEntity<AccountRestorationResponse> restoreAccount(
            @Valid @RequestBody RestoreAccountRequest request,
            HttpServletRequest httpRequest) {

        String ip = metadataExtractor.extractIp(httpRequest);
        String userAgent = metadataExtractor.extractUserAgent(httpRequest);

        AccountRestorationResponse response = userAccountService.restoreByEmail(
                request.getEmail(), request.getPassword(), ip, userAgent);

        log.info("Account restoration completed for email={}", maskEmail(request.getEmail()));
        return ResponseEntity.ok(response);
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***@***";
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
