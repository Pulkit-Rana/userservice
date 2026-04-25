package com.syncnest.userservice.controller;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.SecurityConfig.TokenBlacklistConfig;
import com.syncnest.userservice.dto.*;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.oauth2.OAuth2ExchangeOttService;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.OtpService;
import com.syncnest.userservice.service.PasswordResetService;
import com.syncnest.userservice.service.RefreshTokenService;
import com.syncnest.userservice.service.RegistrationService;
import com.syncnest.userservice.service.UserAccountService;
import com.syncnest.userservice.service.UserSummaryMapper;
import com.syncnest.userservice.utils.RequestMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.syncnest.userservice.logging.LogSanitizer.maskEmail;
import static com.syncnest.userservice.logging.LogSanitizer.maskIp;
import static com.syncnest.userservice.logging.LogSanitizer.maskUserAgent;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String NEXT_SECURED_PATH = "/dashboard";

    /** Set to true in production (HTTPS) so refresh tokens are never sent over clear HTTP. */
    @Value("${app.cookie.secure:false}")
    private boolean refreshCookieSecure;

    /** Cookie max-age on login/verify must match server-side refresh lifetime (see RefreshTokenServiceImpl). */
    @Value("${refresh-token.expiration.milliseconds:2592000000}")
    private long refreshTokenExpirationMs;

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final RegistrationService registrationService;
    private final OtpService otpService;
    private final PasswordResetService passwordResetService;
    private final UserAccountService userAccountService;
    private final RequestMetadataExtractor metadataExtractor;
    private final JwtTokenProviderConfig jwtTokenProvider;
    private final TokenBlacklistConfig tokenBlacklistConfig;
    private final OAuth2ExchangeOttService oauth2ExchangeOttService;
    private final UserSummaryMapper userSummaryMapper;

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.debug("Login request received from IP: {}, UserAgent: {}",
                maskIp(metadataExtractor.extractIp(httpRequest)),
                maskUserAgent(metadataExtractor.extractUserAgent(httpRequest)));

        // Always override IP and User-Agent from real HTTP headers — never trust the request body
        request.setIp(metadataExtractor.extractIp(httpRequest));
        request.setUserAgent(metadataExtractor.extractUserAgent(httpRequest));
        request.setDeviceType(metadataExtractor.parseDeviceType(request.getUserAgent()));

        LoginResponse response = authService.login(request);

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
        response.setRefreshToken(null);

        log.info("Login response prepared for email: {}, accessToken issued", maskEmail(response.getUser().getEmail()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(response);
    }

    /**
     * Exchanges a one-time ticket from the browser Google OAuth redirect for a fresh access JWT.
     * The refresh token remains in the HttpOnly cookie set during the OAuth callback.
     */
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<OAuth2ExchangeResponse> oauth2Exchange(@Valid @RequestBody OAuth2ExchangeRequest request) {
        UUID userId = oauth2ExchangeOttService.consume(request.getOtt());
        if (userId == null) {
            throw new BadCredentialsException("Invalid or expired exchange ticket");
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired exchange ticket"));
        if (!user.isEnabled() || user.isLocked()) {
            throw new BadCredentialsException("Account is not available");
        }
        String accessToken = jwtTokenProvider.generateToken(user.getEmail());
        long expiresIn = jwtTokenProvider.getTokenValiditySeconds();
        OAuth2ExchangeResponse body = OAuth2ExchangeResponse.builder()
                .accessToken(accessToken)
                .expiresIn(expiresIn)
                .user(userSummaryMapper.toSummary(user))
                .build();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        String cookieToken = jwtTokenProvider.resolveRefreshTokenCookie(httpRequest).orElse(null);
        String bodyToken = request != null ? request.getRefreshToken() : null;
        String resolvedToken = (cookieToken != null && !cookieToken.isBlank()) ? cookieToken : bodyToken;
        String deviceId = request != null ? request.getDeviceId() : null;

        String source = (cookieToken != null && !cookieToken.isBlank()) ? "cookie" : "body";
        log.debug("Refresh token request received, deviceId={}, tokenSource={}", deviceId, source);

        RefreshTokenRequest effective = new RefreshTokenRequest();
        effective.setRefreshToken(resolvedToken);
        effective.setDeviceId(deviceId);

        RefreshTokenResponse response = refreshTokenService.validateAndRotate(effective);

        long maxAge = 0;
        if (response.getExpiresAt() != null) {
            long seconds = Instant.now().until(response.getExpiresAt(), ChronoUnit.SECONDS);
            maxAge = Math.max(seconds, 0);
        }

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
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
                .otpSecondsRemaining(status.otpSecondsRemaining())
                .resendLockSecondsRemaining(status.resendLockSecondsRemaining())
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

    @PostMapping("/resend-otp")
    public ResponseEntity<ResendOtpResponse> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {

        log.debug("OTP resend request received for email: {}", maskEmail(request.getEmail()));

        ResendOtpResponse response = otpService.resendOtp(request.getEmail());

        log.info("OTP resend completed for email: {}, used: {}/{}",
                maskEmail(request.getEmail()),
                response.getOtpMeta().getUsed(),
                response.getOtpMeta().getMax());

        return ResponseEntity.ok(response);
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
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();

        response.setRefreshToken(null);

        log.info("OTP verification successful, user logged in: {}", maskEmail(response.getUser().getEmail()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .header(HttpHeaders.LOCATION, NEXT_SECURED_PATH)
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @Valid @RequestBody LogoutRequest request,
            HttpServletRequest httpRequest) {

        // Invalidate the presented access token so it cannot be used until natural expiry
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String access = authHeader.substring(7).trim();
            if (!access.isEmpty()) {
                tokenBlacklistConfig.addToBlacklist(access);
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null) ? auth.getName() : null;

        log.debug("Logout request: principalEmail={} (Bearer may be absent for cookie-only clients)", maskEmail(email));

        User user = (email != null) ? userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null) : null;
        if (user == null) {
            String raw = request.getRefreshToken();
            if (!StringUtils.hasText(raw)) {
                raw = jwtTokenProvider.resolveRefreshTokenCookie(httpRequest).orElse(null);
            }
            if (StringUtils.hasText(raw)) {
                user = refreshTokenService.findUserByRawRefreshToken(raw).orElse(null);
            }
        }

        if (user != null) {
            String uemail = user.getEmail();
            String iss = jwtTokenProvider.getConfiguredIssuer();
            long nowSec = java.time.Instant.now().getEpochSecond();
            long fenceTtlSec = (long) jwtTokenProvider.getTokenValiditySeconds() + 120L;
            tokenBlacklistConfig.setRevocationFence(iss, uemail, nowSec, fenceTtlSec);

            if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
                refreshTokenService.revokeAllForUserDevice(user, request.getDeviceId());
                log.info("Revoked refresh tokens for user={} deviceId={}", maskEmail(uemail), request.getDeviceId());
            } else {
                refreshTokenService.revokeAllForUser(user);
                log.info("Revoked all refresh tokens for user={}", maskEmail(uemail));
            }
        } else if (StringUtils.hasText(email)) {
            // Valid JWT in context but no DB user (e.g. deleted) — still fence by subject
            String iss = jwtTokenProvider.getConfiguredIssuer();
            long nowSec = java.time.Instant.now().getEpochSecond();
            long fenceTtlSec = (long) jwtTokenProvider.getTokenValiditySeconds() + 120L;
            tokenBlacklistConfig.setRevocationFence(iss, email, nowSec, fenceTtlSec);
        } else {
            log.debug("Logout: could not resolve user (no valid JWT + no refresh) — only cookie clear / optional blacklist");
        }

        ResponseCookie clear = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        LogoutResponse body = new LogoutResponse();
        body.setSuccess(true);
        body.setMessage("Logged out");

        String loggedEmail = (user != null) ? user.getEmail() : email;
        log.info("Logout response sent for: {}", maskEmail(loggedEmail));
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
}
