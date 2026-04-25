package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.AuditHistoryResponse;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.RegistrationStatus;
import com.syncnest.userservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface AuditHistoryService {

    /**
     * Records a user-activity audit entry (without device context).
     * Convenience overload — delegates to the full method with null DeviceMetadata.
     */
    void record(
            AuditEventType eventType,
            AuditOutcome outcome,
            User user,
            String userEmail,
            RegistrationStatus registrationStatus,
            String details
    );

    /**
     * Records a user-activity audit entry linked to a specific device.
     *
     * @param eventType          the kind of event (LOGIN, REGISTRATION, OTP_VERIFICATION, …)
     * @param outcome            SUCCESS / FAILURE
     * @param user               the user entity (nullable for pre-registration events)
     * @param userEmail          email (used as fallback when user is null)
     * @param registrationStatus registration lifecycle status (nullable — set for REGISTRATION / OTP events)
     * @param details            free-text details
     * @param deviceMetadata     the device this event occurred on (nullable)
     */
    void record(
            AuditEventType eventType,
            AuditOutcome outcome,
            User user,
            String userEmail,
            RegistrationStatus registrationStatus,
            String details,
            DeviceMetadata deviceMetadata
    );

    Page<AuditHistoryResponse> getHistoryForUser(
            String userEmail,
            AuditEventType eventType,
            AuditOutcome outcome,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            Pageable pageable
    );
}

