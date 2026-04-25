package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.dto.*;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.DeviceType;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.DeviceMetadataService;
import com.syncnest.userservice.service.RefreshTokenService;
import com.syncnest.userservice.service.UserSummaryMapper;
import com.syncnest.userservice.logging.LogSanitizer;
import com.syncnest.userservice.utils.RequestMetadataExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProviderConfig jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuditHistoryService auditHistoryService;
    private final DeviceMetadataService deviceMetadataService;
    private final RequestMetadataExtractor extractor;
    private final UserSummaryMapper userSummaryMapper;

    private final Clock clock = Clock.systemUTC();

    /** {@code @Lazy} defers resolving {@link AuthenticationManager} until first use, breaking a startup cycle with {@code SecurityConfig}. */
    @Autowired
    public AuthServiceImpl(
            @Lazy AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtTokenProviderConfig jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            AuditHistoryService auditHistoryService,
            DeviceMetadataService deviceMetadataService,
            RequestMetadataExtractor extractor,
            UserSummaryMapper userSummaryMapper) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.auditHistoryService = auditHistoryService;
        this.deviceMetadataService = deviceMetadataService;
        this.extractor = extractor;
        this.userSummaryMapper = userSummaryMapper;
    }

    /** Internal result holder so login() can access DeviceMetadata for audit. */
    private record TokenIssuanceResult(LoginResponse loginResponse, DeviceMetadata deviceMetadata) {}

    @Override
    public LoginResponse login(LoginRequest request) throws BadCredentialsException {
        final String email = safeEmail(request.getEmail());
        Objects.requireNonNull(request.getPassword(), "password is required");

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException ex) {
            auditHistoryService.record(AuditEventType.LOGIN, AuditOutcome.FAILURE,
                    null, email, null, "INVALID_CREDENTIALS");
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.isDeleted() || !user.isEnabled() || user.isLocked()) {
            auditHistoryService.record(AuditEventType.LOGIN, AuditOutcome.FAILURE,
                    user, email, null,
                    user.isDeleted() ? "ACCOUNT_DELETED" : "ACCOUNT_SUSPENDED");
            throw new BadCredentialsException("Account Suspended");
        }

        DeviceContext ctx = buildContext(request);
        TokenIssuanceResult result = issueTokensInternal(user, ctx, true);

        auditHistoryService.record(AuditEventType.LOGIN, AuditOutcome.SUCCESS,
                user, email, null, "LOGIN_SUCCESS", result.deviceMetadata());

        log.info("Login success for email={}", LogSanitizer.maskEmail(email));
        return result.loginResponse();
    }

    // ─── issueTokensFor ──────────────────────────────────────────────────────

    @Override
    public LoginResponse issueTokensFor(User user, DeviceContext ctx, boolean recordDeviceMetadata) {
        return issueTokensInternal(user, ctx, recordDeviceMetadata).loginResponse();
    }

    /**
     * Core token-issuance logic. Returns both the LoginResponse and the DeviceMetadata
     * so callers (like login()) can link the device to audit records.
     */
    private TokenIssuanceResult issueTokensInternal(User user, DeviceContext ctx, boolean recordDeviceMetadata) {
        Objects.requireNonNull(user, "user is required");
        if (user.isDeleted() || !user.isEnabled() || user.isLocked()) {
            throw new BadCredentialsException("Account Suspended");
        }

        DeviceContext enriched = enrich(ctx);

        // 1) Sync device upsert — link DeviceMetadata to refresh token
        DeviceMetadata deviceMeta = null;
        if (recordDeviceMetadata) {
            deviceMeta = deviceMetadataService.upsertAndReturn(user, enriched);
        }

        // 2) Access token
        String accessToken = jwtTokenProvider.generateToken(user.getEmail());
        long expiresIn     = jwtTokenProvider.getTokenValiditySeconds();

        // 3) Refresh token bound to deviceId, with human-readable device info and device metadata link
        String deviceInfo = buildDeviceInfo(enriched);
        RefreshTokenResponse rt = refreshTokenService.issue(user, enriched.getDeviceId(), deviceInfo, deviceMeta);

        // 4) Response
        LoginResponse response = LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn(expiresIn)
                .refreshToken(rt.getRefreshToken())
                .deviceId(enriched.getDeviceId())
                .issuedAt(Instant.now(clock))
                .user(userSummaryMapper.toSummary(user))
                .build();

        return new TokenIssuanceResult(response, deviceMeta);
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

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return "unknown";
    }

    private <T> T defaultIfNull(T value, T defaultVal) {
        return value != null ? value : defaultVal;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /**
     * Normalizes a device ID: trims, caps at 64 chars, defaults to "unknown".
     */
    private String normalizeDeviceId(String deviceId) {
        String d = (deviceId == null) ? "" : deviceId.trim();
        if (d.isEmpty()) return "unknown";
        return d.length() > 64 ? d.substring(0, 64) : d;
    }

    /**
     * Builds a human-readable device info string from DeviceContext (e.g., "Chrome on Windows").
     */
    private String buildDeviceInfo(DeviceContext ctx) {
        if (ctx == null) return "Unknown device";
        String browser = ctx.getBrowser();
        String os = ctx.getOs();
        boolean hasBrowser = browser != null && !browser.isBlank() && !"Unknown".equalsIgnoreCase(browser);
        boolean hasOs = os != null && !os.isBlank() && !"Unknown".equalsIgnoreCase(os);

        if (hasBrowser && hasOs) return browser + " on " + os;
        if (hasBrowser) return browser;
        if (hasOs) return os;
        return ctx.getDeviceType() != null ? ctx.getDeviceType().name() : "Unknown device";
    }
}
