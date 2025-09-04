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

/**
 * Refresh token service (prod-grade):
 * - Stores only SHA-256 hash of refresh tokens.
 * - Rotation on each refresh; sliding inactivity window.
 * - Max N concurrent sessions per user (newest session kept, oldest revoked).
 * - Absolute lifetime cap as per refresh-token.expiration.milliseconds.
 * NOTE on "devices":
 * If you don't send a deviceId, we auto-generate a per-session identifier (for observability only).
 * Enforcement is by session count, not by device.
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

    /** Max concurrent sessions per user. */
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
        // If deviceId is not provided, we create a unique session id (not used for enforcement).
        final String sessionId = resolveSessionId(deviceId);

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
                .deviceId(sessionId) // informational only if you aren't supplying deviceId
                .issuedAt(now)
                .expiresAt(finalExpiry)
                .revoked(false)
                .build();

        // Save first so this session is "newest"
        refreshTokenRepo.save(model);

        // Enforce max concurrent sessions AFTER saving => newest-in, oldest-out (FIFO)
        enforceMaxSessions(user, maxDevices);

        RefreshTokenResponse resp = new RefreshTokenResponse();
        resp.setRefreshToken(raw);          // return RAW to client (only hash is stored)
        resp.setExpiresAt(model.getExpiresAt());
        resp.setDeviceId(sessionId);
        return resp;
    }

    @Override
    @Transactional
    public RefreshTokenResponse validateAndRotate(RefreshTokenRequest request) {
        String raw = require(request.getRefreshToken());
        String deviceId = request.getDeviceId();
        String hash = sha256Hex(raw);
        Instant now = Instant.now();

        RefreshToken found = refreshTokenRepo
                .findByTokenHashAndRevokedFalseAndExpiresAtAfter(hash, now)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired refresh token"));

        // Optional device binding (checked only if caller sends a deviceId)
        if (deviceId != null && !deviceId.isBlank() && !found.getDeviceId().equals(deviceId)) {
            throw new IllegalArgumentException("Device mismatch for refresh token");
        }

        // Revoke old (rotation)
        found.setRevoked(true);

        // Re-issue for same user & same session id (slides inactivity window)
        // This will also enforce max sessions (newest-in, oldest-out).
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
        // Keeps API compatibility if you later decide to bind to a real deviceId.
        Instant now = Instant.now();
        refreshTokenRepo.findAllByUserAndDeviceIdAndRevokedFalseAndExpiresAtAfter(user, normalizeDevice(deviceId), now)
                .forEach(t -> t.setRevoked(true));
    }

    @Override
    @Transactional
    public void purgeExpiredAndRevoked() {
        purgeExpiredAndRevoked(Instant.now());
    }

    @Transactional
    @Override
    public void purgeExpiredAndRevoked(Instant now) {
        refreshTokenRepo.deleteAllByExpiresAtBeforeOrRevokedTrue(now);
    }

    // =========================== INTERNALS ===========================

    /**
     * Enforce max concurrent sessions by count (not by device).
     * Keeps the newest 'max' sessions (by issuedAt DESC), revokes the rest.
     */
    private void enforceMaxSessions(User user, int max) {
        Instant now = Instant.now();
        List<RefreshToken> active = refreshTokenRepo.findAllByUserAndRevokedFalseAndExpiresAtAfter(user, now);

        if (active.size() <= max) return;

        // Sort newest first, keep first 'max'
        active.sort(Comparator.comparing(RefreshToken::getIssuedAt).reversed());
        for (int i = max; i < active.size(); i++) {
            active.get(i).setRevoked(true); // oldest first beyond the cap
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

    /**
     * Returns a usable device/session id for storage (≤64 chars).
     * - If a non-blank deviceId is provided, normalize & return it.
     * - Otherwise, generate a stable random session id (for observability).
     */
    private String resolveSessionId(String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) return normalizeDevice(deviceId);
        // 16 random bytes → base64url (~22 chars)
        byte[] buf = new byte[16];
        random.nextBytes(buf);
        String sid = "sess-" + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        return sid.length() > 64 ? sid.substring(0, 64) : sid;
    }

    private String normalizeDevice(String deviceId) {
        String d = (deviceId == null) ? "" : deviceId.trim();
        if (d.isEmpty()) return "unknown";
        return d.length() > 64 ? d.substring(0, 64) : d;
    }

    private static String require(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("refreshToken is required");
        return v;
    }
}
