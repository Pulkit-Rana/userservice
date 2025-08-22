package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.RefreshTokenRequest;
import com.syncnest.userservice.dto.RefreshTokenResponse;
import com.syncnest.userservice.entity.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface RefreshTokenService {

    /** Issue a refresh token for (user, deviceId), enforcing max devices. */
    RefreshTokenResponse issue(User user, String deviceId);

    /** Validate + rotate refresh token (sliding inactivity). */
    RefreshTokenResponse validateAndRotate(RefreshTokenRequest request);

    /** Revoke all active tokens for a user (logout all devices). */
    void revokeAllForUser(User user);

    /** Revoke active token(s) for a specific device (logout that device). */
    void revokeAllForUserDevice(User user, String deviceId);

    /** Housekeeping: delete expired or revoked rows. */
    void purgeExpiredAndRevoked();

    @Transactional
    void purgeExpiredAndRevoked(Instant now);
}
