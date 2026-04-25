package com.syncnest.userservice.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(
        name = "device_metadata",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dm_user_device_id", columnNames = {"user_id", "device_id"})
        },
        indexes = {
                @Index(name = "ix_dm_user_id",        columnList = "user_id"),
                @Index(name = "ix_dm_user_device_id", columnList = "user_id, device_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Client-supplied deviceId, or a server-generated UA fingerprint prefixed with "fp-". */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    /** Real client IP extracted server-side (X-Forwarded-For → RemoteAddr). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Raw User-Agent header as received from the browser/client. */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Operating system parsed from User-Agent (e.g. "Windows 10/11", "Android 13"). */
    @Column(name = "os", length = 100)
    private String os;

    /** Browser parsed from User-Agent (e.g. "Chrome", "Safari", "Postman"). */
    @Column(name = "browser", length = 100)
    private String browser;

    /** Human-readable location resolved from IP via ip-api.com (best-effort). */
    @Column(name = "location", length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 16, nullable = false)
    @Builder.Default
    private DeviceType deviceType = DeviceType.UNKNOWN;

    /** Timestamp of the very first login from this device. Never updated. */
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime firstSeenAt = LocalDateTime.now();

    /** Timestamp updated on every successful login from this device. */
    @Column(name = "last_login_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    /**
     * All refresh tokens issued for this device.
     * Allows querying active sessions per device and revoking them.
     */
    @OneToMany(mappedBy = "deviceMetadata")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<RefreshToken> refreshTokens;

    /**
     * All audit events that occurred on this device.
     * Enables: User → DeviceMetadata → AuditHistory and
     *          RefreshToken → DeviceMetadata → AuditHistory chains.
     */
    @OneToMany(mappedBy = "deviceMetadata")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<AuditHistory> auditHistory;
}
