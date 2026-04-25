package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.dto.RefreshTokenRequest;
import com.syncnest.userservice.dto.RefreshTokenResponse;
import com.syncnest.userservice.logging.LogSanitizer;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.RefreshToken;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.exception.TokenExceptions;
import com.syncnest.userservice.repository.RefreshTokenRepository;
import com.syncnest.userservice.service.AuditHistoryService;
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
 * - Token family tracking for replay-attack detection.
 * - Replay attack detection: if a revoked token is reused, ALL user sessions are revoked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepo;
    private final AuditHistoryService auditHistoryService;
    private final JwtTokenProviderConfig jwtTokenProvider;

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
        log.info("RefreshTokenService initialized: maxDevices={}, inactivitySeconds={}, absoluteLifetimeMs={}, randomBytes={}",
                maxDevices, inactivitySeconds, absoluteLifetimeMs, randomBytes);
    }

    // ─── ISSUE (new login — creates a new rotation family) ──────────────────

    @Override
    @Transactional
    public RefreshTokenResponse issue(User user, String deviceId, String deviceInfo, DeviceMetadata deviceMetadata) {
        // New login → new rotation family
        return issue(user, deviceId, deviceInfo, UUID.randomUUID().toString(), deviceMetadata);
    }

    @Override
    @Transactional
    public RefreshTokenResponse issue(User user, String deviceId, String deviceInfo, String familyId, DeviceMetadata deviceMetadata) {
        final String sessionId = resolveSessionId(deviceId, deviceMetadata);
        final String safeDeviceInfo = truncate(deviceInfo, 255);

        log.debug("Issuing refresh token for user={}, sessionId={}, familyId={}",
                LogSanitizer.maskEmail(user.getEmail()), sessionId, familyId);

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
                .deviceId(sessionId)
                .deviceInfo(safeDeviceInfo)
                .deviceMetadata(deviceMetadata)
                .familyId(familyId)
                .issuedAt(now)
                .expiresAt(finalExpiry)
                .lastActive(now)
                .revoked(false)
                .build();

        // Save first so this session is "newest"
        refreshTokenRepo.save(model);
        log.debug("Refresh token saved: tokenId={}, user={}, familyId={}, expiresAt={}",
                model.getId(), LogSanitizer.maskEmail(user.getEmail()), familyId, finalExpiry);

        // Enforce max concurrent sessions AFTER saving => newest-in, oldest-out (FIFO)
        enforceMaxSessions(user, maxDevices);

        RefreshTokenResponse resp = new RefreshTokenResponse();
        resp.setRefreshToken(raw);          // return RAW to client (only hash is stored)
        resp.setExpiresAt(model.getExpiresAt());
        resp.setDeviceId(sessionId);

        log.info("Refresh token issued for user={}, sessionId={}, familyId={}, expiresAt={}",
                LogSanitizer.maskEmail(user.getEmail()), sessionId, familyId, finalExpiry);
        return resp;
    }

    // ─── VALIDATE & ROTATE (with replay attack detection) ───────────────────

    @Override
    @Transactional
    public RefreshTokenResponse validateAndRotate(RefreshTokenRequest request) {
        String raw = requireToken(request.getRefreshToken());
        String deviceId = request.getDeviceId();
        String hash = sha256Hex(raw);
        Instant now = Instant.now();

        log.debug("Validating refresh token for deviceId: {}", deviceId);

        // ── Step 1: Look up by hash with row lock (replay detection + single-winner rotation).
        // PESSIMISTIC_WRITE prevents two concurrent refreshes from both seeing revoked=false and minting two successors.
        Optional<RefreshToken> lookup = refreshTokenRepo.findByTokenHashForUpdate(hash);

        if (lookup.isEmpty()) {
            // Token hash not found at all — completely invalid token
            log.warn("Refresh token validation failed: token hash not found in database");
            auditHistoryService.record(
                    AuditEventType.REFRESH_TOKEN,
                    AuditOutcome.FAILURE,
                    null, null, null,
                    "REFRESH_TOKEN_NOT_FOUND"
            );
            throw new TokenExceptions.RefreshTokenInvalid("Invalid or expired refresh token");
        }

        RefreshToken found = lookup.get();
        User user = found.getUser();

        // ── Step 2: Already rotated / reused ──
        // With pessimistic locking, a second concurrent refresh waits until the first finishes; the row is then
        // revoked. Treat reuse as invalid (do not mint a second successor). Avoid revoking all sessions here:
        // concurrent legitimate double-submits would otherwise lock the user out.
        if (found.isRevoked()) {
            log.warn("Refresh rejected: token already revoked (reuse or concurrent refresh) for user={}, familyId={}",
                    LogSanitizer.maskEmail(user.getEmail()), found.getFamilyId());
            auditHistoryService.record(
                    AuditEventType.REFRESH_TOKEN,
                    AuditOutcome.FAILURE,
                    user, user.getEmail(), null,
                    "REFRESH_TOKEN_ALREADY_REVOKED",
                    found.getDeviceMetadata()
            );
            throw new TokenExceptions.RefreshTokenInvalid("Refresh token has already been used. Please sign in again.");
        }

        // ── Step 3: Check if token is expired ──
        if (found.getExpiresAt().isBefore(now)) {
            log.warn("Refresh token expired for user={}, expiredAt={}",
                    LogSanitizer.maskEmail(user.getEmail()), found.getExpiresAt());
            auditHistoryService.record(
                    AuditEventType.REFRESH_TOKEN,
                    AuditOutcome.FAILURE,
                    user, user.getEmail(), null,
                    "REFRESH_TOKEN_EXPIRED",
                    found.getDeviceMetadata()
            );
            throw new TokenExceptions.RefreshTokenExpired("Refresh token has expired. Please log in again.");
        }

        // ── Step 4: Check user account status ──
        if (user.isDeleted() || !user.isEnabled() || user.isLocked()) {
            found.setRevoked(true);
            auditHistoryService.record(
                    AuditEventType.REFRESH_TOKEN,
                    AuditOutcome.FAILURE,
                    user, user.getEmail(), null,
                    user.isDeleted() ? "REFRESH_TOKEN_USER_DELETED" : "REFRESH_TOKEN_USER_DISABLED",
                    found.getDeviceMetadata()
            );
            throw new TokenExceptions.RefreshTokenRevoked("Account is no longer active");
        }

        // ── Step 5: Optional device binding check ──
        if (deviceId != null && !deviceId.isBlank() && !found.getDeviceId().equals(deviceId)) {
            log.warn("Refresh token device mismatch for user={}: expected={}, provided={}",
                    LogSanitizer.maskEmail(user.getEmail()), found.getDeviceId(), deviceId);
            auditHistoryService.record(
                    AuditEventType.REFRESH_TOKEN,
                    AuditOutcome.FAILURE,
                    user, user.getEmail(), null,
                    "REFRESH_TOKEN_DEVICE_MISMATCH",
                    found.getDeviceMetadata()
            );
            throw new TokenExceptions.DeviceMismatch("Device mismatch for refresh token");
        }

        // ── Step 6: Revoke old token (rotation) ──
        found.setRevoked(true);
        found.setLastActive(now); // mark the moment of last use before revoking
        log.debug("Refresh token revoked for rotation: user={}, sessionId={}, familyId={}",
                LogSanitizer.maskEmail(user.getEmail()), found.getDeviceId(), found.getFamilyId());

        // ── Step 7: Issue NEW refresh token in the SAME rotation family ──
        RefreshTokenResponse response = issue(
                user,
                found.getDeviceId(),
                found.getDeviceInfo(),
                found.getFamilyId(),  // carry forward the family ID
                found.getDeviceMetadata()  // carry forward the device metadata link
        );

        // ── Step 8: Issue a NEW access token as well ──
        String newAccessToken = jwtTokenProvider.generateToken(user.getEmail());
        long expiresIn = jwtTokenProvider.getTokenValiditySeconds();
        response.setAccessToken(newAccessToken);
        response.setExpiresIn(expiresIn);

        auditHistoryService.record(
                AuditEventType.REFRESH_TOKEN,
                AuditOutcome.SUCCESS,
                user, user.getEmail(), null,
                "REFRESH_TOKEN_ROTATED",
                found.getDeviceMetadata()
        );

        log.info("Refresh token rotated successfully for user={}, familyId={}, newExpiresAt={}",
                LogSanitizer.maskEmail(user.getEmail()), found.getFamilyId(), response.getExpiresAt());
        return response;
    }

    // ─── REVOKE ALL ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void revokeAllForUser(User user) {
        log.debug("Revoking all refresh tokens for user={}", LogSanitizer.maskEmail(user.getEmail()));

        int revokedCount = refreshTokenRepo.revokeAllByUser(user);

        log.info("Revoked {} refresh tokens for user={}", revokedCount, LogSanitizer.maskEmail(user.getEmail()));

        auditHistoryService.record(
                AuditEventType.LOGOUT,
                AuditOutcome.SUCCESS,
                user, user.getEmail(),
                null,
                "LOGOUT_ALL_DEVICES"
        );
    }

    @Override
    @Transactional
    public void revokeAllForUserDevice(User user, String deviceId) {
        log.debug("Revoking refresh tokens for user={}, deviceId={}",
                LogSanitizer.maskEmail(user.getEmail()), deviceId);

        Instant now = Instant.now();
        String normalizedDevice = normalizeDevice(deviceId);
        List<RefreshToken> active = refreshTokenRepo.findAllByUserAndDeviceIdAndRevokedFalseAndExpiresAtAfter(user, normalizedDevice, now);
        active.forEach(t -> t.setRevoked(true));
        long revokedCount = active.size();

        log.info("Revoked {} refresh tokens for user={}, deviceId={}",
                revokedCount, LogSanitizer.maskEmail(user.getEmail()), deviceId);

        auditHistoryService.record(
                AuditEventType.LOGOUT,
                AuditOutcome.SUCCESS,
                user, user.getEmail(),
                null,
                "LOGOUT_SINGLE_DEVICE: " + normalizedDevice
        );
    }

    @Override
    @Transactional
    public Optional<User> findUserByRawRefreshToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepo.findByTokenHash(sha256Hex(raw)).map(RefreshToken::getUser);
    }

    @Override
    @Transactional
    public void purgeExpiredAndRevoked() {
        purgeExpiredAndRevoked(Instant.now());
    }

    @Transactional
    @Override
    public void purgeExpiredAndRevoked(Instant now) {
        // Only remove rows past natural expiry. Revoked rows must remain until then so
        // findByTokenHash can still see reused/rotated tokens for correct replay behavior.
        refreshTokenRepo.deleteAllByExpiresAtBefore(now);
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
            throw new TokenExceptions.TokenHashingFailed("Unable to hash refresh token: " + e.getMessage());
        }
    }

    /**
     * Returns a usable device/session id for storage (≤64 chars).
     */
    private String resolveSessionId(String deviceId, DeviceMetadata deviceMetadata) {
        if (deviceMetadata != null && deviceMetadata.getDeviceId() != null && !deviceMetadata.getDeviceId().isBlank()) {
            return normalizeDevice(deviceMetadata.getDeviceId());
        }
        if (deviceId != null && !deviceId.isBlank()) return normalizeDevice(deviceId);
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

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private static String requireToken(String v) {
        if (v == null || v.isBlank()) {
            throw new TokenExceptions.RefreshTokenRequired("Refresh token must be provided");
        }
        return v;
    }
}
