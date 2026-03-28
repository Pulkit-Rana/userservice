package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.AuditHistoryResponse;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditHistory;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.AuditHistoryRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditHistoryServiceImpl implements AuditHistoryService {

    private final AuditHistoryRepository auditHistoryRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;
    private final Clock clock = Clock.systemUTC();


    @Override
    public void record(
            AuditEventType eventType,
            AuditOutcome outcome,
            User user,
            String userEmail,
            String deviceId,
            String ipAddress,
            String userAgent,
            String location,
            String details
    ) {
        AuditWriteRequest request = new AuditWriteRequest(
                eventType,
                outcome,
                user != null ? user.getId() : null,
                normalizeEmail(userEmail),
                trimToLength(deviceId, 64),
                trimToLength(ipAddress, 45),
                trimToLength(userAgent, 255),
                trimToLength(location, 255),
                trimToLength(details, 500)
        );

        try {
            // Persist after outer commit to avoid lock contention with active auth/OTP transactions.
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        persistSafely(request);
                    }
                });
                return;
            }

            persistSafely(request);
        } catch (Exception ex) {
            // Audit must never break auth flows.
            log.warn("Failed to persist audit history eventType={} userEmail={}", eventType, userEmail, ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditHistoryResponse> getHistoryForUser(
            String userEmail,
            AuditEventType eventType,
            AuditOutcome outcome,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            Pageable pageable
    ) {
        return auditHistoryRepository
                .findHistoryForUser(normalizeEmail(userEmail), eventType, outcome, fromTime, toTime, pageable)
                .map(entry -> AuditHistoryResponse.builder()
                        .eventType(entry.getEventType())
                        .outcome(entry.getOutcome())
                        .userEmail(entry.getUserEmail())
                        .deviceId(entry.getDeviceId())
                        .ipAddress(entry.getIpAddress())
                        .userAgent(entry.getUserAgent())
                        .location(entry.getLocation())
                        .details(entry.getDetails())
                        .occurredAt(entry.getOccurredAt())
                        .build());
    }

    private void persistSafely(AuditWriteRequest request) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> persist(request));
        } catch (Exception ex) {
            // Audit is best-effort and should never fail the caller's API flow.
            log.warn("Failed to persist audit history eventType={} userEmail={}", request.eventType, request.userEmail, ex);
        }
    }

    private void persist(AuditWriteRequest request) {
        User userRef = null;
        if (request.userId != null) {
            userRef = entityManager.getReference(User.class, request.userId);
        }

        AuditHistory model = AuditHistory.builder()
                .user(userRef)
                .eventType(request.eventType)
                .outcome(request.outcome)
                .userEmail(request.userEmail)
                .deviceId(request.deviceId)
                .ipAddress(request.ipAddress)
                .userAgent(request.userAgent)
                .location(request.location)
                .details(request.details)
                .occurredAt(LocalDateTime.now(clock))
                .build();

        auditHistoryRepository.save(model);
    }

    private String normalizeEmail(String providedEmail) {
        String resolved = providedEmail;
        if (resolved == null || resolved.isBlank()) {
            return "unknown";
        }
        return resolved.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToLength(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static final class AuditWriteRequest {
        private final AuditEventType eventType;
        private final AuditOutcome outcome;
        private final UUID userId;
        private final String userEmail;
        private final String deviceId;
        private final String ipAddress;
        private final String userAgent;
        private final String location;
        private final String details;

        private AuditWriteRequest(
                AuditEventType eventType,
                AuditOutcome outcome,
                UUID userId,
                String userEmail,
                String deviceId,
                String ipAddress,
                String userAgent,
                String location,
                String details
        ) {
            this.eventType = eventType;
            this.outcome = outcome;
            this.userId = userId;
            this.userEmail = userEmail;
            this.deviceId = deviceId;
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.location = location;
            this.details = details;
        }
    }
}

