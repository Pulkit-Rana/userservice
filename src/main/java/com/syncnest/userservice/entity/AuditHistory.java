package com.syncnest.userservice.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Tracks user login / registration / OTP-verification activity.
 * Device-specific info (IP, UA, browser, OS, location) is captured
 * in {@link DeviceMetadata}; this table focuses on <em>what</em> and
 * <em>when</em> something happened for a user, plus registration lifecycle status.
 */
@Entity
@Table(name = "audit_history", indexes = {
        @Index(name = "ix_audit_user_id_time", columnList = "user_id, occurred_at"),
        @Index(name = "ix_audit_user_email_time", columnList = "user_email, occurred_at"),
        @Index(name = "ix_audit_type_time", columnList = "event_type, occurred_at"),
        @Index(name = "ix_audit_device_meta", columnList = "device_metadata_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EntityListeners(AuditingEntityListener.class)
public class AuditHistory extends BaseEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "modified_at", insertable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, nullable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "modified_by", insertable = false)
    private String modifiedBy;

    /** The user this activity belongs to (nullable for pre-registration events). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * The device this activity occurred on (nullable — set when device context is available).
     * Enables: RefreshToken → DeviceMetadata → AuditHistory chain.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_metadata_id")
    private DeviceMetadata deviceMetadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    /**
     * Registration lifecycle status — set on REGISTRATION / OTP_VERIFICATION events
     * to track where the user is in the sign-up funnel.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", length = 20)
    private RegistrationStatus registrationStatus;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}

