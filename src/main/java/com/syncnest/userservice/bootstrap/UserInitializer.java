package com.syncnest.userservice.bootstrap;

import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.Profile;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.entity.UserRole;
import com.syncnest.userservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class UserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin.password}")
    private String adminPlainPassword;

    @Value("${app.init.user.password}")
    private String userPlainPassword;

    @Override
    @Transactional
    public void run(String... args) {
        // Add Admin user
        createUserIfNotExists(
                "admin@example.com",
                ensureEncoded(adminPlainPassword),
                UserRole.ROLE_ADMIN,
                "Admin",
                "Super",
                "Admin"
        );

        // Add Normal user
        createUserIfNotExists(
                "user@example.com",
                ensureEncoded(userPlainPassword),
                UserRole.ROLE_USER,
                "Regular",
                "Test",
                "User"
        );
    }

    private void createUserIfNotExists(
            String email,
            String password,
            UserRole role,
            String displayName,
            String firstName,
            String lastName
    ) {
        if (userRepository.existsByEmail(email)) {
            log.info("{} '{}' already exists. Skipping creation.", displayName, email);
            return;
        }

        // Build User
        User user = User.builder()
                .email(email)
                .password(password)
                .role(role)
                .enabled(true)
                .isLocked(false)
                .isVerified(true)
                .provider(AuthProvider.LOCAL)
                .build();

        // Build Profile linked to User
        Profile profile = Profile.builder()
                .user(user)
                .firstName(firstName)
                .lastName(lastName)
                .build();

        // Link both sides (bidirectional mapping)
        user.setProfile(profile);

        userRepository.save(user);

        log.info("{} '{}' with profile added successfully.", displayName, email);
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
