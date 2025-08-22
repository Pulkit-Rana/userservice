package com.syncnest.userservice.repository;

import com.syncnest.userservice.entity.RefreshToken;
import com.syncnest.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Find a token by hash if not revoked and not expired. */
    Optional<RefreshToken> findByTokenHashAndRevokedFalseAndExpiresAtAfter(String tokenHash, Instant now);

    /** All active tokens of a user. */
    List<RefreshToken> findAllByUserAndRevokedFalseAndExpiresAtAfter(User user, Instant now);

    /** All active tokens of a user on a specific device. */
    List<RefreshToken> findAllByUserAndDeviceIdAndRevokedFalseAndExpiresAtAfter(User user, String deviceId, Instant now);

    @Modifying
    @Transactional
    /** Hard delete expired or revoked tokens (for cleanup jobs). */
    void deleteAllByExpiresAtBeforeOrRevokedTrue(Instant expiryThreshold);
}
