package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.dto.LoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.dto.LoginResponse.UserSummary;
import com.syncnest.userservice.dto.RefreshTokenResponse;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProviderConfig jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock = Clock.systemUTC(); // deterministic, testable time source

    @Override
    public LoginResponse login(LoginRequest request) throws BadCredentialsException {
        final String email = safeEmail(request.getEmail());
        final String password = Objects.requireNonNull(request.getPassword(), "password is required");
        final String deviceId = normalizeDeviceId(firstNonBlank(request.getDeviceId(), request.getClientId()));

        // 1) Authenticate (will throw on bad credentials)
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException ex) {
            // Never leak whether email exists
            throw new BadCredentialsException("Invalid email or password");
        }

        // 2) Load user (post-auth for flags, summary)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled() || user.isLocked()) {
            // Hide precise reason from the caller
            throw new BadCredentialsException("Invalid email or password");
        }

        // 3) Access token
        String accessToken = jwtTokenProvider.generateToken(email);
        long expiresIn = jwtTokenProvider.getTokenValiditySeconds();

        // 4) Refresh token (per-device issuance / rotation policy lives in service)
        RefreshTokenResponse rt = refreshTokenService.issue(user, deviceId);

        // 5) Record device metadata (fire-and-forget semantics; no PII beyond what's provided)
        recordDeviceLogin(user, request);

        // 6) Build response (do not log tokens)
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(accessToken);
        resp.setExpiresIn(expiresIn);
        resp.setRefreshToken(rt.getRefreshToken());
        resp.setDeviceId(deviceId);
        resp.setIssuedAt(Instant.now(clock));

        UserSummary us = new UserSummary();
        us.setId(safeId(user.getId()));
        us.setEmail(user.getEmail());
        // If you have a profile name/display name, map it here; fallback to email
        us.setDisplayName(user.getEmail());
        us.setRoles(toRoleSet(user));
        us.setEmailVerified(user.isVerified());
        resp.setUser(us);

        log.info("Login success for email={} deviceId={}", email, deviceId);
        return resp;
    }

    // --- Helpers -------------------------------------------------------------

    private void recordDeviceLogin(User user, LoginRequest request) {
        // Null-safe extraction with sensible defaults
        AuthProvider provider = defaultIfNull(request.getProvider(), AuthProvider.LOCAL);
        DeviceType deviceType = defaultIfNull(request.getDeviceType(), DeviceType.UNKNOWN);
        String location = trimToNull(request.getLocation());

        DeviceMetadata meta = DeviceMetadata.builder()
                .user(user)
                .provider(provider)
                .deviceType(deviceType)
                .location(location)
                .lastLoginAt(LocalDateTime.now(clock))
                .build();

        // Ensure the collection exists (OneToMany cascade will persist child)
        if (user.getDevices() == null) {
            user.setDevices(new HashSet<>());
        }
        user.getDevices().add(meta);

        // Persist the change (cascade = ALL on User.devices)
        userRepository.save(user);
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
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return "unknown";
    }

    private Set<String> toRoleSet(User user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
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
}
