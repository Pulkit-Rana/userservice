package com.syncnest.userservice.bootstrap;

import com.syncnest.userservice.entity.*;
import com.syncnest.userservice.repository.DeviceMetadataRepository;
import com.syncnest.userservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class UserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DeviceMetadataRepository deviceMetadataRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin.password}")
    private String adminPlainPassword;

    @Value("${app.init.user.password}")
    private String userPlainPassword;

    @Override
    @Transactional
    public void run(String... args) {
        // Admin
        User admin = createUserIfNotExists(
                "admin@example.com",
                ensureEncoded(adminPlainPassword),
                UserRole.ROLE_ADMIN,
                "Admin",
                "Super",
                "Admin"
        );
        createTwoDevicesIfMissing(admin);

        // Normal user
        User normal = createUserIfNotExists(
                "user@example.com",
                ensureEncoded(userPlainPassword),
                UserRole.ROLE_USER,
                "Regular",
                "Test",
                "User"
        );
        createTwoDevicesIfMissing(normal);
    }

    private void createTwoDevicesIfMissing(User user) {
        if (user == null) return;

        // Device #1
        upsertSimpleDevice(
                user,
                "New York, US",
                AuthProvider.LOCAL,
                DeviceType.DESKTOP,
                LocalDateTime.now().minusDays(1)
        );

        // Device #2
        upsertSimpleDevice(
                user,
                "San Francisco, US",
                AuthProvider.LOCAL,
                DeviceType.MOBILE,
                LocalDateTime.now().minusHours(12)
        );
    }

    /**
     * Idempotent insert using (user, deviceType, location) as a natural key for bootstrap.
     */
    private void upsertSimpleDevice(
            User user,
            String location,
            AuthProvider provider,
            DeviceType deviceType,
            LocalDateTime lastLoginAt
    ) {
        boolean exists = deviceMetadataRepository
                .existsByUserAndDeviceTypeAndLocation(user, deviceType, location);

        if (exists) {
            log.info("DeviceMetadata already present for user={} type={} location={}",
                    user.getEmail(), deviceType, location);
            return;
        }

        DeviceMetadata dm = DeviceMetadata.builder()
                .user(user)
                .location(location)
                .provider(provider)
                .deviceType(deviceType)
                .lastLoginAt(lastLoginAt)
                .build();

        deviceMetadataRepository.save(dm);
        log.info("Created DeviceMetadata for user={} type={} location={}",
                user.getEmail(), deviceType, location);
    }

    private User createUserIfNotExists(
            String email,
            String password,
            UserRole role,
            String displayName,
            String firstName,
            String lastName
    ) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = User.builder()
                    .email(email)
                    .password(password)
                    .role(role)
                    .enabled(true)
                    .isLocked(false)
                    .isVerified(true)
                    // .provider(AuthProvider.LOCAL)
                    .build();

            Profile profile = Profile.builder()
                    .user(user)
                    .firstName(firstName)
                    .lastName(lastName)
                    .build();

            user.setProfile(profile);
            User saved = userRepository.save(user);
            log.info("{} '{}' with profile added successfully.", displayName, email);
            return saved;
        });
    }

    private String ensureEncoded(String rawOrEncoded) {
        if (rawOrEncoded == null) throw new IllegalArgumentException("Password cannot be null");
        if (isBcrypt(rawOrEncoded)) return rawOrEncoded;
        return passwordEncoder.encode(rawOrEncoded);
    }

    private boolean isBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
