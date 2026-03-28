package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.exception.PasswordExceptions;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.PasswordResetService;
import com.syncnest.userservice.service.RefreshTokenService;
import com.syncnest.userservice.utils.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.token.Sha512DigestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditHistoryService auditHistoryService;
    private final EmailTemplate emailTemplate;

    private static final String RESET_PREFIX = "pwd:reset:";
    private static final Duration RESET_CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final Duration COOLDOWN = Duration.ofMinutes(10);

    private static final int MAX_RESEND_REQUESTS = 5;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private String keyCode(String email)        { return RESET_PREFIX + email + ":code"; }
    private String keyAttempts(String email)    { return RESET_PREFIX + email + ":attempts"; }
    private String keyResendCount(String email) { return RESET_PREFIX + email + ":resend_count"; }
    private String keyResendLock(String email)  { return RESET_PREFIX + email + ":resend_lock"; }
    private String keyCooldown(String email)    { return RESET_PREFIX + email + ":cooldown"; }

    @Override
    public void sendResetCode(String rawEmail, String ipAddress, String userAgent) {
        final String email = normalizeEmail(rawEmail);

        if (redisTemplate.hasKey(keyCooldown(email))) {
            auditHistoryService.record(
                    AuditEventType.PASSWORD_RESET_REQUEST,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "PASSWORD_RESET_REQUEST_COOLDOWN"
            );
            throw new PasswordExceptions.ResetRequestRateLimited("Too many reset requests. Please wait 10 minutes and try again.");
        }
        if (redisTemplate.hasKey(keyResendLock(email))) {
            auditHistoryService.record(
                    AuditEventType.PASSWORD_RESET_REQUEST,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "PASSWORD_RESET_REQUEST_RESEND_LOCK"
            );
            throw new PasswordExceptions.ResetRequestRateLimited("Please wait 1 minute before requesting another reset code.");
        }

        int reqCount = incrementResendCount(email);
        if (reqCount > MAX_RESEND_REQUESTS) {
            redisTemplate.opsForValue().set(keyCooldown(email), "1", COOLDOWN);
            auditHistoryService.record(
                    AuditEventType.PASSWORD_RESET_REQUEST,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "PASSWORD_RESET_REQUEST_LIMIT_REACHED"
            );
            throw new PasswordExceptions.ResetRequestRateLimited("Reset request limit reached. Please try again later.");
        }

        redisTemplate.opsForValue().set(keyResendLock(email), "1", RESEND_INTERVAL);

        Optional<User> userOpt = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (userOpt.isEmpty()) {
            auditHistoryService.record(
                    AuditEventType.PASSWORD_RESET_REQUEST,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "PASSWORD_RESET_USER_NOT_FOUND"
            );
            return;
        }

        User user = userOpt.get();
        String resetCode = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        String hashed = Sha512DigestUtils.shaHex(resetCode);

        redisTemplate.opsForValue().set(keyCode(email), hashed, RESET_CODE_TTL);
        redisTemplate.opsForValue().set(keyAttempts(email), "0", RESET_CODE_TTL);

        try {
            emailTemplate.sendPasswordResetCode(email, resetCode);
        } catch (Exception ex) {
            auditHistoryService.record(
                    AuditEventType.PASSWORD_RESET_REQUEST,
                    AuditOutcome.FAILURE,
                    user,
                    email,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "PASSWORD_RESET_EMAIL_DELIVERY_FAILED"
            );
            throw new PasswordExceptions.PasswordResetFailed("Failed to send reset email. Please try again.");
        }

        auditHistoryService.record(
                AuditEventType.PASSWORD_RESET_REQUEST,
                AuditOutcome.SUCCESS,
                user,
                email,
                null,
                ipAddress,
                userAgent,
                null,
                "PASSWORD_RESET_CODE_SENT"
        );

        log.info("Password reset code issued for email={}", maskEmail(email));
    }

    @Override
    @Transactional
    public void resetPassword(String rawEmail, String resetCode, String newPassword, String ipAddress, String userAgent) {
        final String email = normalizeEmail(rawEmail);
        if (resetCode == null || resetCode.isBlank()) {
            throw new PasswordExceptions.InvalidResetRequest("Reset code is required.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new PasswordExceptions.InvalidResetRequest("New password is required.");
        }

        final String storedHash = redisTemplate.opsForValue().get(keyCode(email));

        if (storedHash == null) {
            auditHistoryService.record(
                    AuditEventType.PASSWORD_RESET,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "PASSWORD_RESET_CODE_EXPIRED"
            );
            throw new PasswordExceptions.ResetCodeExpired("Reset code expired or not found. Please request a new code.");
        }

        String enteredHash = Sha512DigestUtils.shaHex(resetCode);
        if (!enteredHash.equals(storedHash)) {
            int attempts = incrementAttempts(email);
            if (attempts >= MAX_VERIFY_ATTEMPTS) {
                clearKeys(email);
                redisTemplate.opsForValue().set(keyCooldown(email), "1", COOLDOWN);
                auditHistoryService.record(
                        AuditEventType.PASSWORD_RESET,
                        AuditOutcome.FAILURE,
                        null,
                        email,
                        null,
                        ipAddress,
                        userAgent,
                        null,
                        "PASSWORD_RESET_MAX_ATTEMPTS"
                );
                throw new PasswordExceptions.TooManyResetAttempts("Too many invalid reset attempts. Please request a new reset code later.");
            }
            auditHistoryService.record(
                    AuditEventType.PASSWORD_RESET,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "PASSWORD_RESET_CODE_INVALID"
            );
            throw new PasswordExceptions.ResetCodeInvalid("Invalid reset code provided.");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new PasswordExceptions.ResetCodeInvalid("Invalid reset request."));

        if (user.isDeleted()) {
            throw new PasswordExceptions.ResetCodeInvalid("Invalid reset request.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user);
        clearKeys(email);

        auditHistoryService.record(
                AuditEventType.PASSWORD_RESET,
                AuditOutcome.SUCCESS,
                user,
                email,
                null,
                ipAddress,
                userAgent,
                null,
                "PASSWORD_RESET_SUCCESS"
        );

        log.info("Password reset completed for email={}", maskEmail(email));
    }

    private int incrementAttempts(String email) {
        Long val = redisTemplate.opsForValue().increment(keyAttempts(email));
        if (val != null && val == 1L) {
            redisTemplate.expire(keyAttempts(email), RESET_CODE_TTL);
        }
        return Optional.ofNullable(val).map(Long::intValue).orElse(1);
    }

    private int incrementResendCount(String email) {
        Long val = redisTemplate.opsForValue().increment(keyResendCount(email));
        if (val != null && val == 1L) {
            redisTemplate.expire(keyResendCount(email), Duration.ofMinutes(30));
        }
        return Optional.ofNullable(val).map(Long::intValue).orElse(1);
    }

    private void clearKeys(String email) {
        redisTemplate.delete(keyCode(email));
        redisTemplate.delete(keyAttempts(email));
        redisTemplate.delete(keyResendLock(email));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new PasswordExceptions.InvalidResetRequest("Email must be provided.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***@***";
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}

