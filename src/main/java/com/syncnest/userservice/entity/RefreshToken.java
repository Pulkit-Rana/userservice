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
                @Index(name = "ix_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "ix_family_id", columnList = "family_id"),
                @Index(name = "ix_device_meta", columnList = "device_metadata_id")
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

    /**
     * Link to the device this token was issued on.
     * Enables querying all active sessions per device and revoking them.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_metadata_id")
    private DeviceMetadata deviceMetadata;

    /** Canonical device identifier mirrored from linked DeviceMetadata.deviceId. */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    /**
     * Human-readable device metadata (e.g., "Chrome on Windows", "iPhone 15").
     * Populated from User-Agent parsing at issuance time.
     */
    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    /**
     * Token family identifier — groups all tokens in a rotation chain.
     * Set once at initial login; carried forward on every rotation.
     * Used for replay attack detection: if a revoked token within a family
     * is reused, the entire family (and all user sessions) are revoked.
     */
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    /** Issued-at time. */
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    /** Expiry time. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Last time this session was actively used (token presented for rotation).
     * Used to prune stale sessions that haven't been used in a while.
     */
    @Column(name = "last_active", nullable = false)
    private Instant lastActive;

    /** Revoked flag for server-side invalidation. */
    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
