package com.syncnest.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.RegistrationStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditHistoryResponse(
        AuditEventType eventType,
        AuditOutcome outcome,
        String userEmail,
        RegistrationStatus registrationStatus,
        String details,
        LocalDateTime occurredAt,
        /** Device ID from linked DeviceMetadata (null if no device context). */
        String deviceId,
        /** Human-readable device info (e.g. "Chrome on Windows"). */
        String deviceInfo
) {
}

