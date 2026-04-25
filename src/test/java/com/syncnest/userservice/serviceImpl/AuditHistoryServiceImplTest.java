package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.AuditHistoryResponse;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditHistory;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.DeviceMetadata;
import com.syncnest.userservice.entity.RegistrationStatus;
import com.syncnest.userservice.entity.User;
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
import java.util.UUID;

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
                null,
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
    void record_shouldPersistRegistrationStatusWhenProvided() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service.record(
                AuditEventType.REGISTRATION,
                AuditOutcome.SUCCESS,
                null,
                "alice@example.com",
                RegistrationStatus.INITIATED,
                "USER_REGISTERED"
        );

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());

        AuditHistory saved = captor.getValue();
        assertThat(saved.getRegistrationStatus()).isEqualTo(RegistrationStatus.INITIATED);
        assertThat(saved.getDetails()).isEqualTo("USER_REGISTERED");
    }

    @Test
    void getHistoryForUser_shouldMapRepositoryPageToResponseDto() {
        LocalDateTime now = LocalDateTime.now();
        AuditHistory entry = AuditHistory.builder()
                .eventType(AuditEventType.REFRESH_TOKEN)
                .outcome(AuditOutcome.SUCCESS)
                .userEmail("alice@example.com")
                .registrationStatus(null)
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
        assertThat(response.deviceId()).isNull();
        assertThat(response.deviceInfo()).isNull();
    }

    @Test
    void record_withDeviceMetadata_shouldPersistDeviceLink() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        DeviceMetadata mockDevice = DeviceMetadata.builder()
                .id(42L)
                .deviceId("fp-abc123")
                .os("Windows")
                .browser("Chrome")
                .build();

        // Use entityManager.getReference stub
        when(entityManager.getReference(DeviceMetadata.class, 42L)).thenReturn(mockDevice);

        User user = User.builder()
                .email("test@example.com")
                .password("secret")
                .build();
        // Set a UUID on user so the userId path works
        user.setId(UUID.randomUUID());
        when(entityManager.getReference(User.class, user.getId())).thenReturn(user);

        service.record(
                AuditEventType.LOGIN,
                AuditOutcome.SUCCESS,
                user,
                "test@example.com",
                null,
                "LOGIN_SUCCESS",
                mockDevice
        );

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());

        AuditHistory saved = captor.getValue();
        assertThat(saved.getDeviceMetadata()).isNotNull();
        assertThat(saved.getDeviceMetadata().getId()).isEqualTo(42L);
        assertThat(saved.getUser()).isNotNull();
    }

    @Test
    void getHistoryForUser_shouldIncludeDeviceInfoWhenLinked() {
        LocalDateTime now = LocalDateTime.now();
        DeviceMetadata device = DeviceMetadata.builder()
                .id(1L)
                .deviceId("fp-xyz")
                .os("macOS")
                .browser("Safari")
                .build();

        AuditHistory entry = AuditHistory.builder()
                .eventType(AuditEventType.LOGIN)
                .outcome(AuditOutcome.SUCCESS)
                .userEmail("bob@example.com")
                .details("LOGIN_SUCCESS")
                .occurredAt(now)
                .deviceMetadata(device)
                .build();

        when(auditHistoryRepository.findHistoryForUser(anyString(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Page<AuditHistoryResponse> page = service.getHistoryForUser(
                "bob@example.com", null, null, null, null, PageRequest.of(0, 20));

        AuditHistoryResponse response = page.getContent().get(0);
        assertThat(response.deviceId()).isEqualTo("fp-xyz");
        assertThat(response.deviceInfo()).isEqualTo("Safari on macOS");
    }
}
