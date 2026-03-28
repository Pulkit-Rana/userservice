package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.RegistrationRequest;
import com.syncnest.userservice.dto.RegistrationResponse;
import com.syncnest.userservice.entity.Profile;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditHistoryService auditHistoryService;

    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegistrationServiceImpl(userRepository, passwordEncoder, auditHistoryService);
    }

    @Test
    void registerUser_shouldRestoreSoftDeletedUserInPlace() {
        RegistrationRequest request = RegistrationRequest.builder()
                .email("user@example.com")
                .password("Password@123")
                .passwordConfirmation("Password@123")
                .firstName("Restored")
                .lastName("User")
                .build();

        User deletedUser = User.builder()
                .email("user@example.com")
                .password("old-password")
                .enabled(false)
                .isLocked(true)
                .isVerified(true)
                .build();
        deletedUser.setDeletedAt(LocalDateTime.now().minusDays(2));
        deletedUser.setProfile(Profile.builder()
                .user(deletedUser)
                .firstName("Old")
                .lastName("Name")
                .build());

        when(userRepository.existsByEmailAndDeletedAtIsNull("user@example.com")).thenReturn(false);
        when(userRepository.findDeletedByEmail("user@example.com")).thenReturn(Optional.of(deletedUser));
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = service.registerUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved).isSameAs(deletedUser);
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.isLocked()).isTrue();
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getPassword()).isEqualTo("encoded-password");
        assertThat(saved.getProfile().getFirstName()).isEqualTo("Restored");
        assertThat(saved.getProfile().getLastName()).isEqualTo("User");
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getMessage()).contains("restored");
    }
}
