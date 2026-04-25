package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.RefreshTokenRequest;
import com.syncnest.userservice.dto.RefreshTokenResponse;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenService {

    /** Issue a refresh token for (user, deviceId), creating a new rotation family. Links to deviceMetadata if non-null. */
    RefreshTokenResponse issue(User user, String deviceId, String deviceInfo, DeviceMetadata deviceMetadata);

    /** Issue a refresh token continuing an existing rotation family (used internally during rotation). */
    RefreshTokenResponse issue(User user, String deviceId, String deviceInfo, String familyId, DeviceMetadata deviceMetadata);

    /** Validate + rotate refresh token (sliding inactivity). Detects replay attacks. */
    RefreshTokenResponse validateAndRotate(RefreshTokenRequest request);

    /** Revoke all active tokens for a user (logout all devices). */
    void revokeAllForUser(User user);

    /** Revoke active token(s) for a specific device (logout that device). */
    void revokeAllForUserDevice(User user, String deviceId);

    /**
     * Resolve a user from a raw refresh (cookie / body) without rotating — for logout and revocation fence.
     * Looks up by hash; returns a user if a row still exists (including recently revoked rows kept until expiry).
     */
    Optional<User> findUserByRawRefreshToken(String rawRefresh);

    /** Housekeeping: delete only rows whose natural {@code expiresAt} is in the past (keeps revoked rows for replay checks). */
    void purgeExpiredAndRevoked();

    @Transactional
    void purgeExpiredAndRevoked(Instant now);
}
