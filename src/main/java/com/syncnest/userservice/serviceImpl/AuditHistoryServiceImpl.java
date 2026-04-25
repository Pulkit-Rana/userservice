package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.AuditHistoryResponse;
import com.syncnest.userservice.logging.LogSanitizer;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditHistory;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.RegistrationStatus;
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
            RegistrationStatus registrationStatus,
            String details
    ) {
        record(eventType, outcome, user, userEmail, registrationStatus, details, null);
    }

    @Override
    public void record(
            AuditEventType eventType,
            AuditOutcome outcome,
            User user,
            String userEmail,
            RegistrationStatus registrationStatus,
            String details,
            DeviceMetadata deviceMetadata
    ) {
        AuditWriteRequest request = new AuditWriteRequest(
                eventType,
                outcome,
                user != null ? user.getId() : null,
                normalizeEmail(userEmail),
                registrationStatus,
                trimToLength(details, 500),
                deviceMetadata != null ? deviceMetadata.getId() : null
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
            log.warn("Failed to persist audit history eventType={} userEmail={}",
                    eventType, LogSanitizer.maskEmail(userEmail), ex);
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
                .map(entry -> {
                    var dm = entry.getDeviceMetadata();
                    return AuditHistoryResponse.builder()
                            .eventType(entry.getEventType())
                            .outcome(entry.getOutcome())
                            .userEmail(entry.getUserEmail())
                            .registrationStatus(entry.getRegistrationStatus())
                            .details(entry.getDetails())
                            .occurredAt(entry.getOccurredAt())
                            .deviceId(dm != null ? dm.getDeviceId() : null)
                            .deviceInfo(dm != null ? buildDeviceInfo(dm) : null)
                            .build();
                });
    }

    private void persistSafely(AuditWriteRequest request) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> persist(request));
        } catch (Exception ex) {
            // Audit is best-effort and should never fail the caller's API flow.
            log.warn("Failed to persist audit history eventType={} userEmail={}",
                    request.eventType, LogSanitizer.maskEmail(request.userEmail), ex);
        }
    }

    private void persist(AuditWriteRequest request) {
        User userRef = null;
        if (request.userId != null) {
            userRef = entityManager.getReference(User.class, request.userId);
        }

        DeviceMetadata deviceRef = null;
        if (request.deviceMetadataId != null) {
            deviceRef = entityManager.getReference(DeviceMetadata.class, request.deviceMetadataId);
        }

        AuditHistory model = AuditHistory.builder()
                .user(userRef)
                .deviceMetadata(deviceRef)
                .eventType(request.eventType)
                .outcome(request.outcome)
                .userEmail(request.userEmail)
                .registrationStatus(request.registrationStatus)
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

    /** Builds "Browser on OS" from DeviceMetadata, e.g. "Chrome on Windows". */
    private String buildDeviceInfo(DeviceMetadata dm) {
        String browser = dm.getBrowser();
        String os = dm.getOs();
        boolean hasBrowser = browser != null && !browser.isBlank() && !"Unknown".equalsIgnoreCase(browser);
        boolean hasOs = os != null && !os.isBlank() && !"Unknown".equalsIgnoreCase(os);
        if (hasBrowser && hasOs) return browser + " on " + os;
        if (hasBrowser) return browser;
        if (hasOs) return os;
        return dm.getDeviceType() != null ? dm.getDeviceType().name() : null;
    }

    private record AuditWriteRequest(AuditEventType eventType, AuditOutcome outcome, UUID userId, String userEmail,
                                     RegistrationStatus registrationStatus, String details, Long deviceMetadataId) {
    }
}

