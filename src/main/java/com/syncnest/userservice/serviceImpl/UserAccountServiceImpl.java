package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.config.CacheConfig;
import com.syncnest.userservice.logging.LogSanitizer;
import com.syncnest.userservice.dto.AccountDeletionResponse;
import com.syncnest.userservice.dto.AccountRestorationResponse;
import com.syncnest.userservice.dto.ChangePasswordRequest;
import com.syncnest.userservice.dto.MeProfileResponse;
import com.syncnest.userservice.dto.UpdateProfileRequest;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.Profile;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.exception.UserExceptions;
import com.syncnest.userservice.repository.AuditHistoryRepository;
import com.syncnest.userservice.repository.DeviceMetadataRepository;
import com.syncnest.userservice.repository.ProfileRepository;
import com.syncnest.userservice.repository.RefreshTokenRepository;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditHistoryService auditHistoryService;
    private final AuditHistoryRepository auditHistoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceMetadataRepository deviceMetadataRepository;
    private final ProfileRepository profileRepository;
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
                "USER_SOFT_DELETED"
        );

        log.info("Soft deleted user account email={} deletedAt={}", LogSanitizer.maskEmail(normalizedEmail), deletedAt);
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
                    LogSanitizer.maskEmail(normalizedEmail), user.getDeletedAt(), graceCutoff);
            auditHistoryService.record(
                    AuditEventType.USER_RESTORE,
                    AuditOutcome.FAILURE,
                    user,
                    normalizedEmail,
                    null,
                    "RESTORE_GRACE_PERIOD_EXPIRED"
            );
            throw new UserExceptions.UserPreconditionFailed(
                    "Restoration window has expired. The account can no longer be recovered.");
        }

        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Restore rejected: invalid password for email={}", LogSanitizer.maskEmail(normalizedEmail));
            auditHistoryService.record(
                    AuditEventType.USER_RESTORE,
                    AuditOutcome.FAILURE,
                    user,
                    normalizedEmail,
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
                "USER_RESTORED"
        );

        log.info("Restored soft-deleted user account email={} restoredAt={}", LogSanitizer.maskEmail(normalizedEmail), now);
        return AccountRestorationResponse.builder()
                .success(true)
                .message("Account restored successfully. You can now log in.")
                .restoredAt(now)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MeProfileResponse getCurrentProfile(String email) {
        User user = loadActiveUserWithProfile(email);
        return toMeProfile(user);
    }

    @Override
    @Transactional
    public MeProfileResponse updateCurrentProfile(String email, UpdateProfileRequest request) {
        User user = loadActiveUserWithProfile(email);
        Profile profile = user.getProfile();
        if (profile == null) {
            profile = Profile.builder().user(user).build();
            user.setProfile(profile);
        }
        if (request.getFirstName() != null) {
            profile.setFirstName(trimToNull(request.getFirstName(), 100));
        }
        if (request.getLastName() != null) {
            profile.setLastName(trimToNull(request.getLastName(), 100));
        }
        userRepository.save(user);
        auditHistoryService.record(
                AuditEventType.PROFILE_UPDATE,
                AuditOutcome.SUCCESS,
                user,
                user.getEmail(),
                null,
                "PROFILE_UPDATED"
        );
        return toMeProfile(userRepository.findByEmailAndDeletedAtIsNullFetchProfile(normalizeEmail(email))
                .orElse(user));
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalized)
                .orElseThrow(() -> new UserExceptions.UserNotFound("Active user account not found."));

        if (!user.isVerified()) {
            throw new UserExceptions.UserPreconditionFailed("Verify your email before changing your password.");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditHistoryService.record(
                    AuditEventType.PASSWORD_CHANGE,
                    AuditOutcome.FAILURE,
                    user,
                    normalized,
                    null,
                    "PASSWORD_CHANGE_WRONG_CURRENT"
            );
            throw new UserExceptions.InvalidUserInput("Current password is incorrect.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new UserExceptions.InvalidUserInput("New password must be different from your current password.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
        evictUserDetailsCache(normalized);

        auditHistoryService.record(
                AuditEventType.PASSWORD_CHANGE,
                AuditOutcome.SUCCESS,
                user,
                normalized,
                null,
                "PASSWORD_CHANGED"
        );
        log.info("Password changed for user email={}", LogSanitizer.maskEmail(normalized));
    }

    private User loadActiveUserWithProfile(String email) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmailAndDeletedAtIsNullFetchProfile(normalized)
                .orElseThrow(() -> new UserExceptions.UserNotFound("Active user account not found."));
        if (user.isDeleted() || !user.isEnabled() || user.isLocked()) {
            throw new UserExceptions.UserInactive("Account is not available.");
        }
        return user;
    }

    private static String trimToNull(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > maxLen ? t.substring(0, maxLen) : t;
    }

    private MeProfileResponse toMeProfile(User u) {
        Profile p = u.getProfile();
        String displayName = null;
        String firstName = null;
        String lastName = null;
        String picture = null;
        String phone = null;
        String city = null;
        String country = null;
        if (p != null) {
            firstName = p.getFirstName();
            lastName = p.getLastName();
            picture = p.getProfilePictureUrl();
            phone = p.getPhoneNumber();
            city = p.getCity();
            country = p.getCountry();
            if (StringUtils.hasText(p.getFirstName()) || StringUtils.hasText(p.getLastName())) {
                String fn = p.getFirstName() != null ? p.getFirstName().trim() : "";
                String ln = p.getLastName() != null ? p.getLastName().trim() : "";
                displayName = (fn + " " + ln).trim();
            }
        }
        if (!StringUtils.hasText(displayName) && StringUtils.hasText(u.getEmail())) {
            int at = u.getEmail().indexOf('@');
            displayName = at > 0 ? u.getEmail().substring(0, at) : u.getEmail();
        }

        Set<String> roles = Set.of(u.getRole().getValue());

        return MeProfileResponse.builder()
                .id(u.getId() != null ? u.getId().toString() : null)
                .email(u.getEmail())
                .displayName(displayName)
                .profilePictureUrl(picture)
                .roles(roles)
                .emailVerified(u.isVerified())
                .googleLinked(StringUtils.hasText(u.getGoogleSub()))
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber(phone)
                .city(city)
                .country(country)
                .build();
    }

    @Override
    @Transactional
    public int purgeExpiredSoftDeletes() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<User> expired = userRepository.findSoftDeletedWithDeletedAtBefore(cutoff);
        if (expired.isEmpty()) {
            log.debug("Soft-delete purge: none past retention (retentionDays={}, cutoff={}).", retentionDays, cutoff);
            return 0;
        }
        log.info("Soft-delete purge: hard-deleting {} account(s) past retention (retentionDays={}, cutoff={}).",
                expired.size(), retentionDays, cutoff);
        int purged = 0;
        for (User user : expired) {
            String email = user.getEmail();
            auditHistoryService.record(
                    AuditEventType.USER_HARD_DELETE,
                    AuditOutcome.SUCCESS,
                    null,
                    email,
                    null,
                    "RETENTION_PURGE_SCHEDULED_JOB"
            );
            auditHistoryRepository.deleteAllForUserPurge(user.getId(), email);
            refreshTokenRepository.deleteAllByUser(user);
            deviceMetadataRepository.deleteAllByUserId(user.getId());
            profileRepository.deleteByUserId(user.getId());
            evictUserDetailsCache(email);
            userRepository.delete(user);
            purged++;
        }
        return purged;
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
