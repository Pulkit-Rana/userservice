package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.RefreshTokenRequest;
import com.syncnest.userservice.dto.RefreshTokenResponse;
import com.syncnest.userservice.entity.RefreshToken;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.RefreshTokenRepository;
import com.syncnest.userservice.service.RefreshTokenService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Prod-grade refresh token service:
 * - Stores only SHA-256 hash of refresh tokens.
 * - Rotation on each refresh; sliding inactivity window.
 * - Max N devices per user (per distinct deviceId).
 * - Absolute lifetime cap as per refresh-token.expiration.milliseconds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepo;

    /** Sliding inactivity window seconds. Default 7 days if not configured. */
    @Value("${refresh-token.inactivity.seconds:604800}")
    private long inactivitySeconds;

    /** Absolute lifetime cap in ms (respects your existing property). */
    @Value("${refresh-token.expiration.milliseconds:2592000000}")
    private long absoluteLifetimeMs;

    /** Max concurrent devices per user. */
    @Value("${refresh-token.max.count:3}")
    private int maxDevices;

    /** Entropy for raw token before Base64URL. */
    @Value("${refresh-token.random-bytes:64}")
    private int randomBytes;

    private SecureRandom random;

    @PostConstruct
    void init() {
        random = new SecureRandom();
        if (maxDevices < 1) maxDevices = 1;
        if (randomBytes < 32) randomBytes = 32; // >= 256-bit
        if (inactivitySeconds < 60) inactivitySeconds = 60; // sane floor
        if (absoluteLifetimeMs < 60000) absoluteLifetimeMs = 60000;
    }

    // ============================== API ==============================

    @Override
    @Transactional
    public RefreshTokenResponse issue(User user, String deviceId) {
        final String dev = normalizeDevice(deviceId);

        // Single token per device: revoke old active tokens on same device
        revokeAllForUserDevice(user, dev);

        // Enforce max devices across distinct deviceIds (keep newest devices)
        enforceMaxDevices(user, maxDevices);

        // Create raw + hash, set expiry (sliding + absolute cap)
        String raw = generateRawToken();
        String hash = sha256Hex(raw);
        Instant now = Instant.now();
        Instant inactivityExpiry = now.plusSeconds(inactivitySeconds);
        Instant absoluteExpiry   = now.plusMillis(absoluteLifetimeMs);
        Instant finalExpiry      = inactivityExpiry.isBefore(absoluteExpiry) ? inactivityExpiry : absoluteExpiry;

        RefreshToken model = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .deviceId(dev)
                .issuedAt(now)
                .expiresAt(finalExpiry)
                .revoked(false)
                .build();

        refreshTokenRepo.save(model);

        RefreshTokenResponse resp = new RefreshTokenResponse();
        resp.setRefreshToken(raw);          // return RAW to client
        resp.setExpiresAt(model.getExpiresAt());
        resp.setDeviceId(dev);
        return resp;
    }

    @Override
    @Transactional
    public RefreshTokenResponse validateAndRotate(RefreshTokenRequest request) {
        String raw = require(request.getRefreshToken(), "refreshToken");
        String deviceId = request.getDeviceId();
        String hash = sha256Hex(raw);
        Instant now = Instant.now();

        RefreshToken found = refreshTokenRepo
                .findByTokenHashAndRevokedFalseAndExpiresAtAfter(hash, now)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired refresh token"));

        // Optional device binding (recommended ON)
        if (deviceId != null && !deviceId.isBlank() && !found.getDeviceId().equals(deviceId)) {
            throw new IllegalArgumentException("Device mismatch for refresh token");
        }

        // Revoke old (rotation)
        found.setRevoked(true);

        // Re-issue for same user & device (this slides inactivity window)
        return issue(found.getUser(), found.getDeviceId());
    }

    @Override
    @Transactional
    public void revokeAllForUser(User user) {
        Instant now = Instant.now();
        refreshTokenRepo.findAllByUserAndRevokedFalseAndExpiresAtAfter(user, now)
                .forEach(t -> t.setRevoked(true));
    }

    @Override
    @Transactional
    public void revokeAllForUserDevice(User user, String deviceId) {
        Instant now = Instant.now();
        refreshTokenRepo.findAllByUserAndDeviceIdAndRevokedFalseAndExpiresAtAfter(user, normalizeDevice(deviceId), now)
                .forEach(t -> t.setRevoked(true));
    }

    @Override
    @Transactional
    public void purgeExpiredAndRevoked() {
        refreshTokenRepo.deleteAllByExpiresAtBeforeOrRevokedTrue(Instant.now(), true);
    }

    // =========================== INTERNALS ===========================

    private void enforceMaxDevices(User user, int max) {
        Instant now = Instant.now();
        var active = refreshTokenRepo.findAllByUserAndRevokedFalseAndExpiresAtAfter(user, now);

        // Only the latest token per device should remain
        Map<String, RefreshToken> latestPerDevice = active.stream()
                .collect(Collectors.toMap(
                        RefreshToken::getDeviceId,
                        t -> t,
                        (a, b) -> a.getIssuedAt().isAfter(b.getIssuedAt()) ? a : b
                ));

        if (latestPerDevice.size() > max) {
            // Keep N newest devices
            Set<String> keep = latestPerDevice.values().stream()
                    .sorted(Comparator.comparing(RefreshToken::getIssuedAt).reversed())
                    .limit(max)
                    .map(RefreshToken::getDeviceId)
                    .collect(Collectors.toSet());

            // Revoke tokens for devices outside keep-set
            active.forEach(t -> { if (!keep.contains(t.getDeviceId())) t.setRevoked(true); });
        }

        // Collapse duplicates within each kept device (keep newest)
        for (RefreshToken t : active) {
            RefreshToken latest = latestPerDevice.get(t.getDeviceId());
            if (latest != null && !Objects.equals(t.getId(), latest.getId())) {
                t.setRevoked(true);
            }
        }
    }

    private String generateRawToken() {
        byte[] buf = new byte[randomBytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String sha256Hex(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    private String normalizeDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return "unknown";
        String d = deviceId.trim();
        return d.length() > 64 ? d.substring(0, 64) : d;
    }

    private static String require(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " is required");
        return v;
    }
}
