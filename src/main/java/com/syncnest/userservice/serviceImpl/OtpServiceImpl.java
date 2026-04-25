package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.*;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.RegistrationStatus;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.exception.OtpExceptions;
import com.syncnest.userservice.exception.UserExceptions;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.security.ShortCodeHasher;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.OtpService;
import com.syncnest.userservice.utils.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.syncnest.userservice.logging.LogSanitizer.maskEmail;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailTemplate emailService;
    private final UserRepository userRepository;
    private final AuthService  authService;
    private final AuditHistoryService auditHistoryService;
    private final ShortCodeHasher shortCodeHasher;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

        enforceNotInCooldown(email);
        enforceResendInterval(email);

        int current = incrementResendCount(email);
        log.debug("OTP resend count for email: {} is now: {}/{}", maskEmail(email), current, MAX_RESENDS);
        
        if (current > MAX_RESENDS) {
            startCooldown(email);
            log.warn("OTP quota exceeded for email: {} - resend count: {}, starting cooldown", 
                    maskEmail(email), current);
            throw new OtpExceptions.OtpQuotaExceeded(
                    "Too many OTP requests. Please try again after " + COOLDOWN.toMinutes() + " minutes.");
        }

        // Generate OTP (100000–999999) using a CSPRNG; store only HMAC(secret, code) in Redis.
        String otp = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));

        String hashedOtp = shortCodeHasher.hmacHex(otp);
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
            throw new OtpExceptions.OtpDeliveryFailed("Failed to send OTP email. Please try again.");
        }
    }

    /**
     * Resends OTP for a registered-but-unverified user.
     * Validates the user exists and is not yet verified, then generates a fresh OTP.
     */
    @Override
    public ResendOtpResponse resendOtp(String rawEmail) {
        final String email = norm(rawEmail);
        log.debug("OTP resend requested for email: {}", maskEmail(email));

        // Security: only allow resend for existing, unverified users
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (user == null) {
            // Deliberately vague to prevent account enumeration
            log.warn("OTP resend rejected: user not found for email: {}", maskEmail(email));
            throw new OtpExceptions.OtpNotFound("No pending OTP registration found for this email.");
        }
        if (user.isVerified()) {
            log.warn("OTP resend rejected: user already verified for email: {}", maskEmail(email));
            throw new OtpExceptions.OtpNotFound("Account is already verified. Please log in.");
        }

        // Delegate to the core OTP generation (handles cooldown, rate-limit, max-resend)
        generateAndSendOtp(email);

        // Build response with current rate-limit metadata
        OtpStatus status = getOtpStatus(email);
        OtpMeta meta = OtpMeta.builder()
                .used(status.used())
                .max(status.max())
                .cooldown(status.cooldown())
                .resendIntervalLock(status.resendIntervalLock())
                .resendIntervalSeconds(status.resendIntervalSeconds())
                .cooldownSeconds(status.cooldownSeconds())
                .otpSecondsRemaining(status.otpSecondsRemaining())
                .resendLockSecondsRemaining(status.resendLockSecondsRemaining())
                .build();

        log.info("OTP resent successfully for email: {}, used: {}/{}", maskEmail(email), status.used(), status.max());
        return ResendOtpResponse.builder()
                .success(true)
                .message("A new OTP has been sent to your email.")
                .otpMeta(meta)
                .build();
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

        String rawOtp = verifyOTP.getOtp() == null ? "" : verifyOTP.getOtp();
        String digits = rawOtp.replaceAll("\\D", "");
        if (digits.length() != 6) {
            throw new OtpExceptions.OtpInvalidFormat("Enter the 6-digit code from your email.");
        }

        String storedHash = redisTemplate.opsForValue().get(keyOtp(email));
        if (storedHash == null) {
            log.warn("OTP verification failed for email: {} - OTP missing or expired", maskEmail(email));
            auditHistoryService.record(
                    AuditEventType.OTP_VERIFICATION,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    RegistrationStatus.OTP_PENDING,
                    "OTP_MISSING_OR_EXPIRED"
            );
            throw new OtpExceptions.OtpExpired("This code has expired. Request a new one.");
        }

        String hashedEnteredOtp = shortCodeHasher.hmacHex(digits);
        if (!MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hashedEnteredOtp.getBytes(StandardCharsets.UTF_8))) {
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
                        RegistrationStatus.OTP_PENDING,
                        "OTP_MAX_ATTEMPTS_REACHED"
                );
                throw new OtpExceptions.MaxOtpAttemptsExceeded(
                        "Too many incorrect attempts. Please request a new OTP after 5 minutes.");
            }
            auditHistoryService.record(
                    AuditEventType.OTP_VERIFICATION,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    RegistrationStatus.OTP_PENDING,
                    "OTP_INCORRECT"
            );
            throw new OtpExceptions.OtpIncorrect("That code is not correct. Check the email and try again.");
        }

        log.debug("OTP verified successfully for email: {}", maskEmail(email));
        // Success → consume OTP & counters
        deleteOtpKeys(email);

        // 1) Resolve user (managed in this @Transactional method)
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> {
                    log.error("User not found after OTP verification for email: {}", maskEmail(email));
                    return new UserExceptions.UserNotFound(
                            "No pending registration found for this email.");
                });

        if (user.isDeleted()) {
            log.warn("OTP verification blocked for deleted user: {}", maskEmail(email));
            throw new UserExceptions.UserNotFound("Account not available.");
        }

        // 2) Enable/verify/unlock account (refresh tokens are issued only here, after OTP success)
        user.setVerified(true);
        user.setEnabled(true);
        user.setLocked(false);
        log.debug("User account activated for email: {}", maskEmail(email));
        userRepository.save(user);

        // 3) Issue tokens using the real-time device context from the HTTP request
        LoginResponse response = authService.issueTokensFor(user, deviceContext, true);

        auditHistoryService.record(
                AuditEventType.OTP_VERIFICATION,
                AuditOutcome.SUCCESS,
                user,
                email,
                RegistrationStatus.OTP_VERIFIED,
                "OTP_VERIFIED"
        );
        
        log.info("OTP verification successful for email: {}, userId: {}", maskEmail(email), user.getId());
        return response;
    }



    // ==== Helpers ====

    private void enforceNotInCooldown(String email) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(keyCooldown(email)))) {
            log.warn("OTP generation rejected for email: {} - cooldown active", maskEmail(email));
            throw new OtpExceptions.OtpCooldownActive(
                    "Please wait " + COOLDOWN.toMinutes() + " minutes before requesting another OTP.");
        }
    }

    private void enforceResendInterval(String email) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(keyResendLock(email)))) {
            log.warn("OTP generation rejected for email: {} - resend interval active", maskEmail(email));
            throw new OtpExceptions.OtpResendRateLimited(
                    "Please wait " + RESEND_INTERVAL.toSeconds() + " seconds before requesting another OTP.");
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
        boolean inCooldown = Boolean.TRUE.equals(redisTemplate.hasKey(keyCooldown(email)));
        boolean lockedForResendInterval = Boolean.TRUE.equals(redisTemplate.hasKey(keyResendLock(email)));
        int resends = Optional.ofNullable(redisTemplate.opsForValue().get(keyResendCount(email)))
                .map(Integer::parseInt)
                .orElse(0);

        log.debug("OTP status check for email: {} - cooldown={}, resendLocked={}, resendCount={}", 
                maskEmail(email), inCooldown, lockedForResendInterval, resends);

        int otpRemaining = 0;
        Long otpTtl = redisTemplate.getExpire(keyOtp(email), TimeUnit.SECONDS);
        if (otpTtl != null && otpTtl > 0) {
            otpRemaining = (int) Math.min(otpTtl, Integer.MAX_VALUE);
        }

        int resendLockRemaining = 0;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(keyResendLock(email)))) {
            Long lockTtl = redisTemplate.getExpire(keyResendLock(email), TimeUnit.SECONDS);
            if (lockTtl != null && lockTtl > 0) {
                resendLockRemaining = (int) Math.min(lockTtl, Integer.MAX_VALUE);
            } else if (lockTtl != null && lockTtl == -1L) {
                resendLockRemaining = (int) RESEND_INTERVAL.toSeconds();
            }
        }

        return new OtpStatus(
                inCooldown,
                lockedForResendInterval,
                resends,
                MAX_RESENDS,
                (int) RESEND_INTERVAL.toSeconds(),
                (int) COOLDOWN.toSeconds(),
                otpRemaining,
                resendLockRemaining
        );
    }
}
