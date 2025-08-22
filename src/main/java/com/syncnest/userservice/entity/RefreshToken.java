package com.syncnest.userservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * RefreshTokenEntity:
 * - Stores only SHA-256 hash of the refresh token (never the raw token).
 * - Linked to User entity via @ManyToOne.
 * - Supports per-device tracking and revoke flag.
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "ix_user", columnList = "user_id"),
                @Index(name = "ix_user_device", columnList = "user_id, device_id"),
                @Index(name = "ix_token_hash", columnList = "token_hash", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hashed (SHA-256) value of the refresh token. */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /** Link to User (foreign key). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Optional device identifier (to revoke per-device). */
    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    /** Issued-at time (epoch seconds). */
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    /** Expiry time (epoch seconds). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Revoked flag for server-side invalidation. */
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;
}
