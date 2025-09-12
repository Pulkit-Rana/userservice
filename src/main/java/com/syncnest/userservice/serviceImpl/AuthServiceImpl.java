package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.dto.*;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.DeviceType;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
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

    private final Clock clock = Clock.systemUTC();

    @Override
    public LoginResponse login(LoginRequest request) throws BadCredentialsException {
        final String email = safeEmail(request.getEmail());
        final String password = Objects.requireNonNull(request.getPassword(), "password is required");

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled() || user.isLocked()) {
            throw new BadCredentialsException("Account Suspended");
        }

        // Build DeviceContext from the request (nulls are fine)
        DeviceContext ctx = DeviceContext.builder()
                .deviceId(request.getDeviceId())
                .clientId(request.getClientId())
                .location(request.getLocation())
                .provider(defaultIfNull(request.getProvider(), AuthProvider.LOCAL))
                .deviceType(defaultIfNull(request.getDeviceType(), DeviceType.UNKNOWN))
                .build();

        LoginResponse resp = issueTokensFor(user, ctx, true);

        log.info("Login success for email={}", email);
        return resp;
    }

    @Override
    public LoginResponse issueTokensFor(User user, DeviceContext ctx, boolean recordDeviceMetadata) {
        Objects.requireNonNull(user, "user is required");

        // Normalize + default the context once
        NormalizedContext nctx = normalize(ctx);

        // 1) Access token + validity
        String accessToken = jwtTokenProvider.generateToken(user.getEmail());
        long expiresIn = jwtTokenProvider.getTokenValiditySeconds();

        // 2) Refresh token bound to normalized deviceId
        RefreshTokenResponse rt = refreshTokenService.issue(user, nctx.deviceId());

        // 3) Optionally record device metadata (audit)
        if (recordDeviceMetadata) {
            recordDeviceLogin(user, nctx);
        }

        // 4) User summary
        UserSummary summary = UserSummary.builder()
                .id(safeId(user.getId()))
                .email(user.getEmail())
                // If you store a display name in Profile, map here:
                // .displayName(user.getProfile() != null ? user.getProfile().getFullName() : null)
                .emailVerified(user.isVerified())
                .build();

        // 5) Response
        return LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn(expiresIn)
                .refreshToken(rt.getRefreshToken())
                .issuedAt(Instant.now(clock))
                .user(summary)
                .build();
    }

    // -------------------- helpers --------------------

    private void recordDeviceLogin(User user, NormalizedContext nctx) {
        DeviceMetadata meta = DeviceMetadata.builder()
                .user(user)
                .provider(nctx.provider())
                .deviceType(nctx.deviceType())
                .location(nctx.location())
                .lastLoginAt(LocalDateTime.now(clock))
                .build();

        if (user.getDevices() == null) {
            user.setDevices(new HashSet<>());
        }
        user.getDevices().add(meta);
        userRepository.save(user);
    }

    /** Single place for device normalization + defaults. */
    private NormalizedContext normalize(DeviceContext raw) {
        // deviceId policy: deviceId > clientId > "unknown"; trim; cap length
        String deviceId = firstNonBlank(raw.getDeviceId(), raw.getClientId());
        deviceId = normalizeDeviceId(deviceId);

        AuthProvider provider = defaultIfNull(raw.getProvider(), AuthProvider.LOCAL);
        DeviceType deviceType = defaultIfNull(raw.getDeviceType(), DeviceType.UNKNOWN);
        String location = trimToNull(raw.getLocation());

        return new NormalizedContext(deviceId, provider, deviceType, location);
    }

    /** Compact record for normalized values. */
    private record NormalizedContext(String deviceId, AuthProvider provider, DeviceType deviceType, String location) {}

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

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String safe(String s) { return s == null ? "null" : s; }
}