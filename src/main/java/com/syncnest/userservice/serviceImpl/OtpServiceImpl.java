package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.*;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.OtpService;
import com.syncnest.userservice.utils.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.token.Sha512DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailTemplate emailService;
    private final UserRepository userRepository;
    private final AuthService  authService;
    private final AuditHistoryService auditHistoryService;

    // ==== Config ====
    private static final String OTP_PREFIX = "otp:signup:";
    private static final Duration OTP_EXPIRY = Duration.ofMinutes(5);

    private static final int MAX_RESENDS = 4;                  // total sends allowed (after the first, or total including first? Here: total including first send)
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private static final int MAX_VERIFY_ATTEMPTS = 5;

    // ==== Keys ====
    private String keyOtp(String email)            { return OTP_PREFIX + email + ":code"; }
    private String keyResendCount(String email)    { return OTP_PREFIX + email + ":resends"; }
    private String keyResendLock(String email)     { return OTP_PREFIX + email + ":resend_lock"; }   // 1-min lock
    private String keyCooldown(String email)       { return OTP_PREFIX + email + ":cooldown"; }      // 5-min cooldown
    private String keyAttempts(String email)       { return OTP_PREFIX + email + ":attempts"; }      // wrong attempts

    private String norm(String email) {
        if (!StringUtils.hasText(email)) throw new IllegalArgumentException("Email must be provided.");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // ==== Public API ====

    /**
     * Generates a 6-digit OTP, stores its hash with TTL, and emails the user.
     * Enforces: cooldown, 1-min resend interval, and max 4 sends.
     */
    @Override
    public void generateAndSendOtp(String rawEmail) {
        final String email = norm(rawEmail);
        log.debug("OTP generation requested for email: {}", maskEmail(email));

        try {
            enforceNotInCooldown(email);
        } catch (IllegalArgumentException ex) {
            log.warn("OTP generation rejected for email: {} - cooldown active", maskEmail(email));
            throw ex;
        }
        
        try {
            enforceResendInterval(email);
        } catch (IllegalArgumentException ex) {
            log.warn("OTP generation rejected for email: {} - resend interval active", maskEmail(email));
            throw ex;
        }

        int current = incrementResendCount(email);
        log.debug("OTP resend count for email: {} is now: {}/{}", maskEmail(email), current, MAX_RESENDS);
        
        if (current > MAX_RESENDS) {
            startCooldown(email);
            log.warn("OTP quota exceeded for email: {} - resend count: {}, starting cooldown", 
                    maskEmail(email), current);
            throw new IllegalArgumentException("Too many OTP requests. Please try again after 5 minutes.");
        }

        // Generate OTP (000000–999999, but avoid leading zeros confusion → 100000–999999)
        String otp = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));

        // Hash & store with TTL
        String hashedOtp = Sha512DigestUtils.shaHex(otp);
        redisTemplate.opsForValue().set(keyOtp(email), hashedOtp, OTP_EXPIRY);

        // Reset wrong attempts counter with the same TTL
        redisTemplate.opsForValue().set(keyAttempts(email), "0", OTP_EXPIRY);

        // Set resend lock for 1 minute (prevent spamming)
        redisTemplate.opsForValue().set(keyResendLock(email), "1", RESEND_INTERVAL);

        // Send the OTP
        try {
            emailService.sendOtp(email, otp);
            log.info("OTP generated and sent to email: {}, resend count: {}", maskEmail(email), current);
        } catch (Exception ex) {
            log.error("Failed to send OTP email to: {} - error: {}", maskEmail(email), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Verifies the OTP and consumes it on success.
     * Enforces: max wrong attempts; starts cooldown on too many failures.
     */
    @Override
    @Transactional
    public LoginResponse verifyAndConsumeOtpOrThrow(VerifyOTPRequest verifyOTP, DeviceContext deviceContext) {
        final String email = norm(verifyOTP.getEmail());
        log.debug("OTP verification attempted for email: {}", maskEmail(email));

        String storedHash = redisTemplate.opsForValue().get(keyOtp(email));
        if (storedHash == null) {
            log.warn("OTP verification failed for email: {} - OTP missing or expired", maskEmail(email));
            auditHistoryService.record(
                    AuditEventType.OTP_VERIFICATION,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    null,
                    null,
                    null,
                    "OTP_MISSING_OR_EXPIRED"
            );
            throw new IllegalArgumentException("OTP expired or invalid.");
        }

        String hashedEnteredOtp = Sha512DigestUtils.shaHex(verifyOTP.getOtp());
        if (!hashedEnteredOtp.equals(storedHash)) {
            int attempts = incrementAttempts(email);
            log.warn("OTP verification failed for email: {} - incorrect OTP, attempts: {}/{}", 
                    maskEmail(email), attempts, MAX_VERIFY_ATTEMPTS);
            
            if (attempts >= MAX_VERIFY_ATTEMPTS) {
                deleteOtpKeys(email);
                startCooldown(email);
                log.error("OTP max attempts reached for email: {}, starting cooldown", maskEmail(email));
                auditHistoryService.record(
                        AuditEventType.OTP_VERIFICATION,
                        AuditOutcome.FAILURE,
                        null,
                        email,
                        null,
                        null,
                        null,
                        null,
                        "OTP_MAX_ATTEMPTS_REACHED"
                );
                throw new IllegalArgumentException(
                        "Too many incorrect attempts. Please request a new OTP after 5 minutes."
                );
            }
            auditHistoryService.record(
                    AuditEventType.OTP_VERIFICATION,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    null,
                    null,
                    null,
                    "OTP_INCORRECT"
            );
            throw new IllegalArgumentException("Incorrect OTP.");
        }

        log.debug("OTP verified successfully for email: {}", maskEmail(email));
        // Success → consume OTP & counters
        deleteOtpKeys(email);

        // 1) Resolve user (managed in this @Transactional method)
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> {
                    log.error("User not found after OTP verification for email: {}", maskEmail(email));
                    return new IllegalArgumentException("User not found");
                });

        if (user.isDeleted()) {
            log.warn("OTP verification blocked for deleted user: {}", maskEmail(email));
            throw new IllegalArgumentException("User not found");
        }

        // 2) Enable/verify/unlock account here (persists on tx commit)
        user.setVerified(true);
        user.setEnabled(true);
        user.setLocked(false);
        log.debug("User account activated for email: {}", maskEmail(email));
        // No explicit save() needed if 'user' is managed; call save if your setup requires.

        // 3) Issue tokens using the real-time device context from the HTTP request
        LoginResponse response = authService.issueTokensFor(user, deviceContext, true);

        auditHistoryService.record(
                AuditEventType.OTP_VERIFICATION,
                AuditOutcome.SUCCESS,
                user,
                email,
                null,
                null,
                null,
                null,
                "OTP_VERIFIED"
        );
        
        log.info("OTP verification successful for email: {}, userId: {}", maskEmail(email), user.getId());
        return response;
    }



    // ==== Helpers ====

    private void enforceNotInCooldown(String email) {
        if (redisTemplate.hasKey(keyCooldown(email))) {
            throw new IllegalArgumentException("Please wait 5 minutes before requesting another OTP.");
        }
    }

    private void enforceResendInterval(String email) {
        if (redisTemplate.hasKey(keyResendLock(email))) {
            throw new IllegalArgumentException("Please wait 1 minute before requesting another OTP.");
        }
    }

    private int incrementResendCount(String email) {
        String key = keyResendCount(email);
        Long val = redisTemplate.opsForValue().increment(key);
        // Ensure the resend counter has a sensible TTL (tie it to the OTP flow window)
        if (val != null && val == 1L) {
            // First time we see this counter: set TTL (e.g., 30 minutes window; adjust as needed)
            redisTemplate.expire(key, Duration.ofMinutes(30));
        }
        return Optional.ofNullable(val).map(Long::intValue).orElse(1);
    }

    private int incrementAttempts(String email) {
        String key = keyAttempts(email);
        Long val = redisTemplate.opsForValue().increment(key);
        // keep attempts TTL aligned with current OTP TTL
        if (val != null && val == 1L) {
            redisTemplate.expire(key, OTP_EXPIRY);
        }
        return Optional.ofNullable(val).map(Long::intValue).orElse(1);
    }

    private void startCooldown(String email) {
        redisTemplate.opsForValue().set(keyCooldown(email), "1", COOLDOWN);
    }

    private void deleteOtpKeys(String email) {
        redisTemplate.delete(keyOtp(email));
        redisTemplate.delete(keyAttempts(email));
        redisTemplate.delete(keyResendLock(email));
        // Intentionally keep resend count so users can't game the quota in one window.
        // Cooldown is set only on abuse/too many attempts or quota exceeded.
    }

    // (Optional) expose a check if the user can request an OTP now; useful for UI
    @Override
    public OtpStatus getOtpStatus(String rawEmail) {
        final String email = norm(rawEmail);
        boolean inCooldown = redisTemplate.hasKey(keyCooldown(email));
        boolean lockedForResendInterval = redisTemplate.hasKey(keyResendLock(email));
        int resends = Optional.ofNullable(redisTemplate.opsForValue().get(keyResendCount(email)))
                .map(Integer::parseInt)
                .orElse(0);

        log.debug("OTP status check for email: {} - cooldown={}, resendLocked={}, resendCount={}", 
                maskEmail(email), inCooldown, lockedForResendInterval, resends);

        return new OtpStatus(
                inCooldown,
                lockedForResendInterval,
                resends,
                MAX_RESENDS,
                (int) RESEND_INTERVAL.toSeconds(),
                (int) COOLDOWN.toSeconds()
        );
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***@***";
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
