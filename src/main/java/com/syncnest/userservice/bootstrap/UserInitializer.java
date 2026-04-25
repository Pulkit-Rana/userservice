//package com.syncnest.userservice.bootstrap;
//
//import com.syncnest.userservice.entity.*;
//import com.syncnest.userservice.repository.AuditHistoryRepository;
//import com.syncnest.userservice.repository.DeviceMetadataRepository;
//import com.syncnest.userservice.repository.UserRepository;
//import org.springframework.transaction.annotation.Transactional;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.core.annotation.Order;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.Locale;
//
//// To be deleted in production once setup done.
//@Slf4j
//@Component
//@Order(1)
//@RequiredArgsConstructor
//public class UserInitializer implements CommandLineRunner {
//
//    private static final String ADMIN_EMAIL = "admin@example.com";
//    private static final String USER_EMAIL = "user@example.com";
//
//    private final UserRepository userRepository;
//    private final DeviceMetadataRepository deviceMetadataRepository;
//    private final AuditHistoryRepository auditHistoryRepository;
//    private final PasswordEncoder passwordEncoder;
//
//    @Value("${app.init.admin.password}")
//    private String adminPlainPassword;
//
//    @Value("${app.init.user.password}")
//    private String userPlainPassword;
//
//    @Override
//    @Transactional
//    public void run(String... args) {
//        SeedResult admin = createUserIfNotExists(
//                ADMIN_EMAIL,
//                ensureEncoded(adminPlainPassword),
//                UserRole.ROLE_ADMIN,
//                "Admin",
//                "Super",
//                "Admin"
//        );
//
//        SeedResult normal = createUserIfNotExists(
//                USER_EMAIL,
//                ensureEncoded(userPlainPassword),
//                UserRole.ROLE_USER,
//                "Regular",
//                "Test",
//                "User"
//        );
//
//        if (!admin.created() && !normal.created()) {
//            log.info("Bootstrap users already exist. Skipping startup seed data.");
//            return;
//        }
//
//        if (admin.created()) {
//            createBootstrapDevicesAndAudit(admin.user(), "admin");
//        }
//        if (normal.created()) {
//            createBootstrapDevicesAndAudit(normal.user(), "user");
//        }
//    }
//
//    private void createBootstrapDevicesAndAudit(User user, String keyPrefix) {
//        if (user == null) return;
//
//        upsertSimpleDevice(
//                user,
//                keyPrefix + "-desktop",
//                "Delhi, India",
//                DeviceType.DESKTOP,
//                LocalDateTime.now().minusDays(1)
//        );
//
//        upsertSimpleDevice(
//                user,
//                keyPrefix + "-mobile",
//                "San Francisco, US",
//                DeviceType.MOBILE,
//                LocalDateTime.now().minusHours(12)
//        );
//
//        createBootstrapAuditIfMissing(user, keyPrefix + "-desktop");
//    }
//
//    private void upsertSimpleDevice(
//            User user,
//            String deviceId,
//            String location,
//            DeviceType deviceType,
//            LocalDateTime lastLoginAt
//    ) {
//        boolean exists = deviceMetadataRepository
//                .findByUserAndDeviceId(user, deviceId)
//                .isPresent();
//
//        if (exists) {
//            log.info("DeviceMetadata already present for user={} deviceId={}", user.getEmail(), deviceId);
//            return;
//        }
//
//        DeviceMetadata dm = DeviceMetadata.builder()
//                .user(user)
//                .deviceId(deviceId)
//                .ipAddress("127.0.0.1")
//                .userAgent("bootstrap-initializer")
//                .os("N/A")
//                .browser("N/A")
//                .location(location)
//                .provider(AuthProvider.LOCAL)
//                .deviceType(deviceType)
//                .firstSeenAt(lastLoginAt.minusMinutes(5))
//                .lastLoginAt(lastLoginAt)
//                .build();
//
//        deviceMetadataRepository.save(dm);
//        log.info("Created DeviceMetadata for user={} deviceId={}", user.getEmail(), deviceId);
//    }
//
//    private void createBootstrapAuditIfMissing(User user, String deviceId) {
//        String normalizedEmail = user.getEmail().trim().toLowerCase(Locale.ROOT);
//        boolean exists = auditHistoryRepository.existsByUserEmailAndEventTypeAndDetails(
//                normalizedEmail,
//                AuditEventType.REGISTRATION,
//                "BOOTSTRAP_USER_CREATED"
//        );
//
//        if (exists) {
//            log.info("Audit bootstrap entry already present for user={} details={}", normalizedEmail, "BOOTSTRAP_USER_CREATED");
//            return;
//        }
//
//        AuditHistory audit = AuditHistory.builder()
//                .user(user)
//                .eventType(AuditEventType.REGISTRATION)
//                .outcome(AuditOutcome.SUCCESS)
//                .userEmail(normalizedEmail)
//                .registrationStatus(RegistrationStatus.COMPLETE)
//                .details("BOOTSTRAP_USER_CREATED")
//                .occurredAt(LocalDateTime.now())
//                .build();
//
//        auditHistoryRepository.save(audit);
//        log.info("Created bootstrap audit for user={} details={}", normalizedEmail, "BOOTSTRAP_USER_CREATED");
//    }
//
//    private SeedResult createUserIfNotExists(
//            String email,
//            String password,
//            UserRole role,
//            String displayName,
//            String firstName,
//            String lastName
//    ) {
//        return userRepository.findByEmailAndDeletedAtIsNull(email)
//                .map(existing -> {
//                    log.info("{} '{}' already exists. Skipping user creation.", displayName, email);
//                    return new SeedResult(existing, false);
//                })
//                .or(() -> userRepository.findDeletedByEmail(email)
//                        .map(existing -> {
//                            log.info("{} '{}' exists in soft-deleted state. Leaving it untouched.", displayName, email);
//                            return new SeedResult(existing, false);
//                        }))
//                .orElseGet(() -> {
//                    User user = User.builder()
//                            .email(email)
//                            .password(password)
//                            .role(role)
//                            .enabled(true)
//                            .isLocked(false)
//                            .isVerified(true)
//                            .build();
//
//                    Profile profile = Profile.builder()
//                            .user(user)
//                            .firstName(firstName)
//                            .lastName(lastName)
//                            .build();
//
//                    user.setProfile(profile);
//                    User saved = userRepository.save(user);
//                    log.info("{} '{}' with profile added successfully.", displayName, email);
//                    return new SeedResult(saved, true);
//                });
//    }
//
//    private String ensureEncoded(String rawOrEncoded) {
//        if (rawOrEncoded == null) throw new IllegalArgumentException("Password cannot be null");
//        if (isBcrypt(rawOrEncoded)) return rawOrEncoded;
//        return passwordEncoder.encode(rawOrEncoded);
//    }
//
//    private boolean isBcrypt(String value) {
//        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
//    }
//
//    private record SeedResult(User user, boolean created) {
//    }
//}
