package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.config.CacheConfig;
import com.syncnest.userservice.dto.AccountDeletionResponse;
import com.syncnest.userservice.dto.AccountRestorationResponse;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.exception.UserExceptions;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.RefreshTokenService;
import com.syncnest.userservice.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditHistoryService auditHistoryService;
    private final CacheManager cacheManager;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.soft-delete.grace-period-days:30}")
    private int gracePeriodDays;

    @Value("${app.soft-delete.retention-days:90}")
    private int retentionDays;

    @Override
    @Transactional
    public AccountDeletionResponse softDeleteByEmail(String email, String ipAddress, String userAgent) {
        String normalizedEmail = normalizeEmail(email);

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new UserExceptions.UserNotFound("Active user account not found."));

        LocalDateTime deletedAt = LocalDateTime.now();
        user.setDeletedAt(deletedAt);
        user.setEnabled(false);
        user.setLocked(true);
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user);
        evictUserDetailsCache(normalizedEmail);

        auditHistoryService.record(
                AuditEventType.USER_SOFT_DELETE,
                AuditOutcome.SUCCESS,
                user,
                user.getEmail(),
                null,
                ipAddress,
                userAgent,
                null,
                "USER_SOFT_DELETED"
        );

        log.info("Soft deleted user account email={} deletedAt={}", normalizedEmail, deletedAt);
        return AccountDeletionResponse.builder()
                .success(true)
                .message("Account deleted successfully. Re-register with the same email, sign in with Google, or use account restore to reactivate it.")
                .deletedAt(deletedAt)
                .build();
    }

    @Override
    @Transactional
    public AccountRestorationResponse restoreByEmail(String email, String password,
                                                     String ipAddress, String userAgent) {
        String normalizedEmail = normalizeEmail(email);

        User user = userRepository.findDeletedByEmail(normalizedEmail)
                .orElseThrow(() -> new UserExceptions.UserNotFound(
                        "No deleted account found for this email."));

        LocalDateTime graceCutoff = user.getDeletedAt().plusDays(gracePeriodDays);
        if (LocalDateTime.now().isAfter(graceCutoff)) {
            log.warn("Restore rejected: grace period expired for email={}, deletedAt={}, graceCutoff={}",
                    normalizedEmail, user.getDeletedAt(), graceCutoff);
            auditHistoryService.record(
                    AuditEventType.USER_RESTORE,
                    AuditOutcome.FAILURE,
                    user,
                    normalizedEmail,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "RESTORE_GRACE_PERIOD_EXPIRED"
            );
            throw new UserExceptions.UserPreconditionFailed(
                    "Restoration window has expired. The account can no longer be recovered.");
        }

        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Restore rejected: invalid password for email={}", normalizedEmail);
            auditHistoryService.record(
                    AuditEventType.USER_RESTORE,
                    AuditOutcome.FAILURE,
                    user,
                    normalizedEmail,
                    null,
                    ipAddress,
                    userAgent,
                    null,
                    "RESTORE_INVALID_PASSWORD"
            );
            throw new UserExceptions.InvalidUserInput("Invalid credentials. Cannot restore account.");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setDeletedAt(null);
        user.setEnabled(true);
        user.setLocked(false);
        userRepository.save(user);

        evictUserDetailsCache(normalizedEmail);

        auditHistoryService.record(
                AuditEventType.USER_RESTORE,
                AuditOutcome.SUCCESS,
                user,
                normalizedEmail,
                null,
                ipAddress,
                userAgent,
                null,
                "USER_RESTORED"
        );

        log.info("Restored soft-deleted user account email={} restoredAt={}", normalizedEmail, now);
        return AccountRestorationResponse.builder()
                .success(true)
                .message("Account restored successfully. You can now log in.")
                .restoredAt(now)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public int purgeExpiredSoftDeletes() {
        int softDeletedUsers = userRepository.findAllSoftDeleted().size();
        if (softDeletedUsers > 0) {
            log.info("Soft-delete purge skipped: {} deleted user(s) retained in the database by design (retentionDays={}).",
                    softDeletedUsers, retentionDays);
        } else {
            log.debug("Soft-delete purge skipped: no deleted users found.");
        }
        return 0;
    }

    private void evictUserDetailsCache(String email) {
        Cache cache = cacheManager.getCache(CacheConfig.USER_DETAILS_BY_EMAIL);
        if (cache != null) {
            cache.evict(email);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new UserExceptions.InvalidUserInput("Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
