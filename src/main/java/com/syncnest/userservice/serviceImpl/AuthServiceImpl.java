package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.dto.*;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceType;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.DeviceMetadataService;
import com.syncnest.userservice.service.RefreshTokenService;
import com.syncnest.userservice.utils.RequestMetadataExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProviderConfig jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuditHistoryService auditHistoryService;
    private final DeviceMetadataService deviceMetadataService;
    private final RequestMetadataExtractor extractor;

    private final Clock clock = Clock.systemUTC();

    // ─── login ───────────────────────────────────────────────────────────────

    @Override
    public LoginResponse login(LoginRequest request) throws BadCredentialsException {
        final String email = safeEmail(request.getEmail());
        Objects.requireNonNull(request.getPassword(), "password is required");

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException ex) {
            auditHistoryService.record(AuditEventType.LOGIN, AuditOutcome.FAILURE,
                    null, email,
                    request.getDeviceId(), request.getIp(), request.getUserAgent(),
                    null, "INVALID_CREDENTIALS");
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.isDeleted() || !user.isEnabled() || user.isLocked()) {
            auditHistoryService.record(AuditEventType.LOGIN, AuditOutcome.FAILURE,
                    user, email,
                    request.getDeviceId(), request.getIp(), request.getUserAgent(),
                    null, user.isDeleted() ? "ACCOUNT_DELETED" : "ACCOUNT_SUSPENDED");
            throw new BadCredentialsException("Account Suspended");
        }

        DeviceContext ctx = buildContext(request);
        LoginResponse resp = issueTokensFor(user, ctx, true);

        auditHistoryService.record(AuditEventType.LOGIN, AuditOutcome.SUCCESS,
                user, email,
                request.getDeviceId(), request.getIp(), request.getUserAgent(),
                null, "LOGIN_SUCCESS");

        log.info("Login success for email={}", email);
        return resp;
    }

    // ─── issueTokensFor ──────────────────────────────────────────────────────

    @Override
    public LoginResponse issueTokensFor(User user, DeviceContext ctx, boolean recordDeviceMetadata) {
        Objects.requireNonNull(user, "user is required");
        if (user.isDeleted() || !user.isEnabled() || user.isLocked()) {
            throw new BadCredentialsException("Account Suspended");
        }

        DeviceContext enriched = enrich(ctx);

        // 1) Access token
        String accessToken = jwtTokenProvider.generateToken(user.getEmail());
        long expiresIn     = jwtTokenProvider.getTokenValiditySeconds();

        // 2) Refresh token bound to deviceId
        RefreshTokenResponse rt = refreshTokenService.issue(user, enriched.getDeviceId());

        // 3) Async device upsert — best effort, never blocks auth
        if (recordDeviceMetadata) {
            deviceMetadataService.upsertDeviceLogin(user, enriched);
        }

        // 4) Response
        return LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn(expiresIn)
                .refreshToken(rt.getRefreshToken())
                .deviceId(enriched.getDeviceId())
                .issuedAt(Instant.now(clock))
                .user(UserSummary.builder()
                        .id(safeId(user.getId()))
                        .email(user.getEmail())
                        .emailVerified(user.isVerified())
                        .build())
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds an initial DeviceContext from a LoginRequest.
     * IP and User-Agent are expected to already be set on the request
     * (overwritten by the controller from real HTTP headers before this call).
     */
    private DeviceContext buildContext(LoginRequest request) {
        String ua = request.getUserAgent();
        return DeviceContext.builder()
                .deviceId(request.getDeviceId())
                .clientId(request.getClientId())
                .ip(request.getIp())
                .userAgent(ua)
                .os(extractor.parseOs(ua))
                .browser(extractor.parseBrowser(ua))
                .provider(defaultIfNull(request.getProvider(), AuthProvider.LOCAL))
                .deviceType(extractor.parseDeviceType(ua))
                .build();
    }

    /**
     * Normalizes a raw DeviceContext: applies defaults, parses UA, caps deviceId length.
     * Always returns a fully populated (non-null field) context.
     */
    private DeviceContext enrich(DeviceContext raw) {
        if (raw == null) raw = DeviceContext.builder().build();

        String ua       = nvl(raw.getUserAgent());
        String os       = raw.getOs() != null ? raw.getOs() : extractor.parseOs(ua);
        String browser  = raw.getBrowser() != null ? raw.getBrowser() : extractor.parseBrowser(ua);
        DeviceType type = raw.getDeviceType() != null && raw.getDeviceType() != DeviceType.UNKNOWN
                ? raw.getDeviceType() : extractor.parseDeviceType(ua);

        String deviceId = firstNonBlank(raw.getDeviceId(), raw.getClientId());
        deviceId = normalizeDeviceId(deviceId);

        return DeviceContext.builder()
                .deviceId(deviceId)
                .clientId(raw.getClientId())
                .ip(nvl(raw.getIp()))
                .userAgent(ua)
                .os(os)
                .browser(browser)
                .provider(defaultIfNull(raw.getProvider(), AuthProvider.LOCAL))
                .deviceType(type)
                .location(raw.getLocation())
                .build();
    }

    private String safeEmail(String email) {
        if (email == null) throw new BadCredentialsException("Invalid email or password");
        return email.trim().toLowerCase();
    }

    private String normalizeDeviceId(String deviceId) {
        String d = (deviceId == null || deviceId.isBlank()) ? "unknown" : deviceId.trim();
        return d.length() > 64 ? d.substring(0, 64) : d;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return "unknown";
    }

    private String safeId(UUID id) {
        return id != null ? id.toString() : null;
    }

    private <T> T defaultIfNull(T value, T defaultVal) {
        return value != null ? value : defaultVal;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
