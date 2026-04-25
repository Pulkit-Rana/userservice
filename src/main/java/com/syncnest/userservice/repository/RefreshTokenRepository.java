package com.syncnest.userservice.repository;

import com.syncnest.userservice.entity.RefreshToken;
import com.syncnest.userservice.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find a token by hash — regardless of revoked/expired status.
     * Used for replay attack detection: if found and already revoked,
     * we know a stolen token is being reused.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Same lookup as {@link #findByTokenHash(String)} but with a row lock so concurrent refresh
     * requests cannot both observe {@code revoked=false} and rotate the same token twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    /** Find a token by hash if not revoked and not expired. */
    Optional<RefreshToken> findByTokenHashAndRevokedFalseAndExpiresAtAfter(String tokenHash, Instant now);

    /** All active tokens of a user. */
    List<RefreshToken> findAllByUserAndRevokedFalseAndExpiresAtAfter(User user, Instant now);

    /** All active tokens of a user on a specific device. */
    List<RefreshToken> findAllByUserAndDeviceIdAndRevokedFalseAndExpiresAtAfter(User user, String deviceId, Instant now);

    /**
     * Revoke all tokens in a rotation family (bulk update).
     * Used when replay attack is detected to kill the entire chain.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.familyId = :familyId AND rt.revoked = false")
    int revokeAllByFamilyId(@Param("familyId") String familyId);

    /**
     * Revoke all active tokens for a given user (bulk update).
     * Used as nuclear option when replay attack is detected.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
    int revokeAllByUser(@Param("user") User user);

    /**
     * Delete refresh token rows past their natural expiry. Revoked but not yet expired rows are kept so
     * {@link #findByTokenHash(String)} still finds them for refresh-token rotation replay detection.
     */
    @Modifying
    @Transactional
    void deleteAllByExpiresAtBefore(Instant expiryThreshold);

    /** Hard delete all refresh-token rows for a user (retention purge after soft-delete). */
    @Modifying
    @Transactional
    void deleteAllByUser(User user);
}
