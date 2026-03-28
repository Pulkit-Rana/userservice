package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                cacheManager,
                passwordEncoder
        );
    }

    @Test
    void purgeExpiredSoftDeletes_shouldRetainSoftDeletedUsers() {
        User deletedUser = User.builder()
                .email("deleted@example.com")
                .password("secret")
                .build();
        deletedUser.setDeletedAt(LocalDateTime.now().minusDays(30));

        when(userRepository.findAllSoftDeleted()).thenReturn(List.of(deletedUser));

        int purged = service.purgeExpiredSoftDeletes();

        assertThat(purged).isZero();
        verify(userRepository).findAllSoftDeleted();
        verifyNoInteractions(refreshTokenService, auditHistoryService, cacheManager);
    }
}
