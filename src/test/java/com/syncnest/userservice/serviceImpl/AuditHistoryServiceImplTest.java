package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.AuditHistoryResponse;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditHistory;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.repository.AuditHistoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditHistoryServiceImplTest {

    @Mock
    private AuditHistoryRepository auditHistoryRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private EntityManager entityManager;

    private AuditHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditHistoryServiceImpl(auditHistoryRepository, transactionManager, entityManager);
    }

    @Test
    void record_shouldPersistSanitizedAuditEntry() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service.record(
                AuditEventType.LOGIN,
                AuditOutcome.SUCCESS,
                null,
                "  User@Email.com ",
                "device-1",
                "10.0.0.1",
                "Mozilla/5.0",
                "Pune",
                "LOGIN_SUCCESS"
        );

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());

        AuditHistory saved = captor.getValue();
        assertThat(saved.getUserEmail()).isEqualTo("user@email.com");
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.LOGIN);
        assertThat(saved.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void getHistoryForUser_shouldMapRepositoryPageToResponseDto() {
        LocalDateTime now = LocalDateTime.now();
        AuditHistory entry = AuditHistory.builder()
                .eventType(AuditEventType.REFRESH_TOKEN)
                .outcome(AuditOutcome.SUCCESS)
                .userEmail("alice@example.com")
                .deviceId("device-22")
                .details("REFRESH_TOKEN_ROTATED")
                .occurredAt(now)
                .build();

        when(auditHistoryRepository.findHistoryForUser(anyString(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Page<AuditHistoryResponse> page = service.getHistoryForUser(
                "ALICE@EXAMPLE.COM",
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        AuditHistoryResponse response = page.getContent().get(0);
        assertThat(response.userEmail()).isEqualTo("alice@example.com");
        assertThat(response.eventType()).isEqualTo(AuditEventType.REFRESH_TOKEN);
        assertThat(response.details()).isEqualTo("REFRESH_TOKEN_ROTATED");
    }
}

