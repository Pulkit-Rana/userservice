package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.dto.GoogleLoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.dto.UserSummary;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private AuditHistoryService auditHistoryService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtDecoder jwtDecoder;

    private GoogleAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthServiceImpl(userRepository, authService, auditHistoryService, passwordEncoder);
        ReflectionTestUtils.setField(service, "jwtDecoder", jwtDecoder);
    }

    @Test
    void loginWithGoogle_shouldRestoreSoftDeletedUser() {
        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("google-token")
                .clientId("web-client")
                .deviceId("device-123")
                .build();

        DeviceContext context = DeviceContext.builder()
                .clientId("web-client")
                .deviceId("device-123")
                .ip("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .provider(AuthProvider.GOOGLE)
                .build();

        Jwt jwt = new Jwt(
                "google-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "email", "user@example.com",
                        "email_verified", true,
                        "sub", "google-sub-1",
                        "aud", List.of("client"),
                        "iss", "https://accounts.google.com"
                )
        );

        User deletedUser = User.builder()
                .email("user@example.com")
                .password("secret")
                .enabled(false)
                .isLocked(true)
                .isVerified(true)
                .googleSub("old-sub")
                .build();
        deletedUser.setDeletedAt(LocalDateTime.now().minusDays(1));

        LoginResponse loginResponse = LoginResponse.builder()
                .deviceId("device-123")
                .user(UserSummary.builder().email("user@example.com").build())
                .build();

        when(jwtDecoder.decode("google-token")).thenReturn(jwt);
        when(userRepository.findByGoogleSubAndDeletedAtIsNull("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findDeletedByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findDeletedByEmail("user@example.com")).thenReturn(Optional.of(deletedUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authService.issueTokensFor(any(User.class), any(DeviceContext.class), eq(true))).thenReturn(loginResponse);

        LoginResponse response = service.loginWithGoogle(request, context);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User restored = userCaptor.getValue();

        assertThat(restored).isSameAs(deletedUser);
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.isEnabled()).isTrue();
        assertThat(restored.isLocked()).isFalse();
        assertThat(restored.isVerified()).isTrue();
        assertThat(restored.getGoogleSub()).isEqualTo("google-sub-1");
        assertThat(restored.getGoogleLinkedAt()).isNotNull();
        assertThat(response).isSameAs(loginResponse);
    }
}
