package com.syncnest.userservice.repository;

import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditHistory;
import com.syncnest.userservice.entity.AuditOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
    Page<AuditHistory> findHistoryForUser(
            @Param("userEmail") String userEmail,
            @Param("eventType") AuditEventType eventType,
            @Param("outcome") AuditOutcome outcome,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable
    );

    boolean existsByUserEmailAndEventTypeAndDetails(String userEmail, AuditEventType eventType, String details);
}
