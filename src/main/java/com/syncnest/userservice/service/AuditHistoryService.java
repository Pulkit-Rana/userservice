package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.AuditHistoryResponse;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface AuditHistoryService {

    void record(
            AuditEventType eventType,
            AuditOutcome outcome,
            User user,
            String userEmail,
            String deviceId,
            String ipAddress,
            String userAgent,
            String location,
            String details
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

