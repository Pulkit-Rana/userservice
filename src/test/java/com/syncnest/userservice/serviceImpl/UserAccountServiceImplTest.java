package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.AuditHistoryRepository;
import com.syncnest.userservice.repository.DeviceMetadataRepository;
import com.syncnest.userservice.repository.ProfileRepository;
import com.syncnest.userservice.repository.RefreshTokenRepository;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditHistoryService auditHistoryService;

    @Mock
    private AuditHistoryRepository auditHistoryRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private DeviceMetadataRepository deviceMetadataRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private UserAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountServiceImpl(
                userRepository,
                refreshTokenService,
                auditHistoryService,
                auditHistoryRepository,
                refreshTokenRepository,
                deviceMetadataRepository,
                profileRepository,
                cacheManager,
                passwordEncoder
        );
        ReflectionTestUtils.setField(service, "retentionDays", 90);
        ReflectionTestUtils.setField(service, "gracePeriodDays", 30);
    }

    @Test
    void purgeExpiredSoftDeletes_shouldHardDeleteUsersPastRetention() {
        UUID id = UUID.randomUUID();
        User deletedUser = User.builder()
                .email("deleted@example.com")
                .password("secret")
                .build();
        deletedUser.setId(id);
        deletedUser.setDeletedAt(LocalDateTime.now().minusDays(91));

        when(userRepository.findSoftDeletedWithDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(deletedUser));

        int purged = service.purgeExpiredSoftDeletes();

        assertThat(purged).isEqualTo(1);
        verify(auditHistoryService).record(
                eq(AuditEventType.USER_HARD_DELETE),
                eq(AuditOutcome.SUCCESS),
                isNull(),
                eq("deleted@example.com"),
                isNull(),
                eq("RETENTION_PURGE_SCHEDULED_JOB")
        );
        verify(auditHistoryRepository).deleteAllForUserPurge(id, "deleted@example.com");
        verify(refreshTokenRepository).deleteAllByUser(deletedUser);
        verify(deviceMetadataRepository).deleteAllByUserId(id);
        verify(profileRepository).deleteByUserId(id);
        verify(userRepository).delete(deletedUser);
    }

    @Test
    void purgeExpiredSoftDeletes_shouldSkipWhenNonePastRetention() {
        when(userRepository.findSoftDeletedWithDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        int purged = service.purgeExpiredSoftDeletes();

        assertThat(purged).isZero();
        verify(userRepository).findSoftDeletedWithDeletedAtBefore(any(LocalDateTime.class));
        verifyNoInteractions(
                auditHistoryService,
                auditHistoryRepository,
                refreshTokenRepository,
                deviceMetadataRepository,
                profileRepository);
        verify(userRepository, never()).delete(any());
        verifyNoInteractions(refreshTokenService, cacheManager);
    }
}
