package com.syncnest.userservice.dto;

import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AuditHistoryResponse(
        AuditEventType eventType,
        AuditOutcome outcome,
        String userEmail,
        String deviceId,
        String ipAddress,
        String userAgent,
        String location,
        String details,
        LocalDateTime occurredAt
) {
}

