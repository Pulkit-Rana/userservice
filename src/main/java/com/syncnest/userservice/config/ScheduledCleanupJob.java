package com.syncnest.userservice.config;

import com.syncnest.userservice.service.RefreshTokenService;
import com.syncnest.userservice.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs for housekeeping:
 *  1. Permanently purge soft-deleted users past the retention window.
 *  2. Clean up expired / revoked refresh tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledCleanupJob {

    private final UserAccountService userAccountService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Runs daily at 03:00 AM — permanently removes soft-deleted users
     * whose deletion date exceeds the configured retention period.
     */
    @Scheduled(cron = "${app.soft-delete.purge-cron:0 0 3 * * ?}")
    public void purgeSoftDeletedUsers() {
        log.info("Scheduled job: purging soft-deleted users past retention period...");
        try {
            int purged = userAccountService.purgeExpiredSoftDeletes();
            log.info("Scheduled job: purged {} expired soft-deleted user(s).", purged);
        } catch (Exception ex) {
            log.error("Scheduled job: failed to purge soft-deleted users", ex);
        }
    }

    /**
     * Runs daily at 04:00 AM — removes refresh token rows only after their {@code expiresAt} (revoked
     * but not yet expired rows are kept for rotation replay detection).
     */
    @Scheduled(cron = "${refresh-token.purge-cron:0 0 4 * * ?}")
    public void purgeExpiredRefreshTokens() {
        log.info("Scheduled job: purging expired/revoked refresh tokens...");
        try {
            refreshTokenService.purgeExpiredAndRevoked();
            log.info("Scheduled job: refresh token cleanup complete.");
        } catch (Exception ex) {
            log.error("Scheduled job: failed to purge refresh tokens", ex);
        }
    }
}

