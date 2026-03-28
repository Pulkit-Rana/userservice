package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.OtpMeta;
import com.syncnest.userservice.dto.RegistrationRequest;
import com.syncnest.userservice.dto.RegistrationResponse;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.Profile;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.entity.UserRole;
import com.syncnest.userservice.exception.UserExceptions;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditHistoryService auditHistoryService;

    @Override
    @Transactional
    public RegistrationResponse registerUser(RegistrationRequest request) {
        log.debug("Registration attempt initiated for email: {}", maskEmail(request.getEmail()));

        final String email = request.getEmail() == null ? null
                : request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (email == null || email.isEmpty()) {
            log.warn("Registration validation failed: email is null or empty");
            throw new IllegalArgumentException("Email must be provided.");
        }

        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            log.warn("Registration rejected: active user already exists with email={}", maskEmail(email));
            throw new UserExceptions.UserAlreadyExists(email);
        }

        User restoredUser = userRepository.findDeletedByEmail(email)
                .map(deleted -> restoreSoftDeletedUser(deleted, request))
                .orElse(null);

        User saved;
        String message;
        if (restoredUser != null) {
            saved = restoredUser;
            message = "Your deleted account has been restored. Verify the OTP sent to your email to reactivate it.";
            log.info("Soft-deleted user restored in-place during registration: email={}, userId={}",
                    maskEmail(saved.getEmail()), saved.getId());
        } else {
            log.debug("Building new user entity for email: {}", maskEmail(email));
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(UserRole.ROLE_USER)
                    .isLocked(true)
                    .isVerified(false)
                    .enabled(false)
                    .build();

            Profile profile = Profile.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .user(user)
                    .build();
            user.setProfile(profile);

            saved = userRepository.save(user);
            message = "User registered successfully. OTP sent to email.";
            log.info("User registered successfully: email={}, userId={}", maskEmail(saved.getEmail()), saved.getId());
        }

        auditHistoryService.record(
                AuditEventType.REGISTRATION,
                AuditOutcome.SUCCESS,
                saved,
                saved.getEmail(),
                null,
                null,
                null,
                null,
                "USER_REGISTERED"
        );

        OtpMeta otpMeta = OtpMeta.builder()
                .used(1).max(3).cooldown(false).resendIntervalLock(false)
                .resendIntervalSeconds(60).cooldownSeconds(300)
                .build();

        return RegistrationResponse.builder()
                .success(true)
                .message(message)
                .email(saved.getEmail())
                .otpMeta(otpMeta)
                .build();
    }

    private User restoreSoftDeletedUser(User deletedUser, RegistrationRequest request) {
        deletedUser.setDeletedAt(null);
        deletedUser.setPassword(passwordEncoder.encode(request.getPassword()));
        deletedUser.setEnabled(false);
        deletedUser.setLocked(true);
        deletedUser.setVerified(false);

        Profile profile = deletedUser.getProfile();
        if (profile == null) {
            profile = Profile.builder()
                    .user(deletedUser)
                    .build();
            deletedUser.setProfile(profile);
        }

        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());

        return userRepository.save(deletedUser);
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***@***";
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
