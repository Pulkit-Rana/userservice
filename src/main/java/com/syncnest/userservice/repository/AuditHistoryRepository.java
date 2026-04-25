package com.syncnest.userservice.repository;

import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditHistory;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditHistoryRepository extends JpaRepository<AuditHistory, UUID> {

    @Query("""
            SELECT a FROM AuditHistory a
            WHERE a.userEmail = :userEmail
              AND (:eventType IS NULL OR a.eventType = :eventType)
              AND (:outcome IS NULL OR a.outcome = :outcome)
              AND (:fromTime IS NULL OR a.occurredAt >= :fromTime)
              AND (:toTime IS NULL OR a.occurredAt <= :toTime)
            """)
    @EntityGraph(attributePaths = "deviceMetadata")
    Page<AuditHistory> findHistoryForUser(
            @Param("userEmail") String userEmail,
            @Param("eventType") AuditEventType eventType,
            @Param("outcome") AuditOutcome outcome,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable
    );

    boolean existsByUserEmailAndEventTypeAndDetails(String userEmail, AuditEventType eventType, String details);

    /** Most recent successful login for a user. */
    Optional<AuditHistory> findTopByUserEmailAndEventTypeAndOutcomeOrderByOccurredAtDesc(
            String email,
            AuditEventType eventType,
            AuditOutcome outcome
    );

    /** Latest registration-status entry for a user (shows where they are in the sign-up funnel). */
    Optional<AuditHistory> findTopByUserEmailAndRegistrationStatusIsNotNullOrderByOccurredAtDesc(String email);

    /** Count distinct successful login events for a user (total login count). */
    @Query("SELECT COUNT(a) FROM AuditHistory a WHERE a.userEmail = :email AND a.eventType = 'LOGIN' AND a.outcome = 'SUCCESS'")
    long countSuccessfulLogins(@Param("email") String email);

    // ─── Device-aware queries (leverage new AuditHistory ↔ DeviceMetadata link) ──

    /** All audit events for a specific device (paginated, newest-first). */
    @Query("""
            SELECT a FROM AuditHistory a
            WHERE a.deviceMetadata = :device
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditHistory> findByDeviceMetadata(@Param("device") DeviceMetadata device, Pageable pageable);

    /** All audit events for a specific device (non-paginated list). */
    List<AuditHistory> findAllByDeviceMetadataOrderByOccurredAtDesc(DeviceMetadata deviceMetadata);

    /** Most recent successful login on a specific device. */
    Optional<AuditHistory> findTopByDeviceMetadataAndEventTypeAndOutcomeOrderByOccurredAtDesc(
            DeviceMetadata device,
            AuditEventType eventType,
            AuditOutcome outcome
    );

    /** All audit events for a user on a specific device (for session-history view). */
    @Query("""
            SELECT a FROM AuditHistory a
            WHERE a.userEmail = :email
              AND a.deviceMetadata.id = :deviceMetadataId
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditHistory> findByUserEmailAndDeviceMetadataId(
            @Param("email") String email,
            @Param("deviceMetadataId") Long deviceMetadataId,
            Pageable pageable
    );

    /** Hard-delete audit rows for a user being purged after retention (PII cleanup). */
    @Modifying
    @Query("DELETE FROM AuditHistory a WHERE a.user.id = :userId OR a.userEmail = :email")
    int deleteAllForUserPurge(@Param("userId") UUID userId, @Param("email") String email);
}
