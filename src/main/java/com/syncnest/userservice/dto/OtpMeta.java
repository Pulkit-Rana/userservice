package com.syncnest.userservice.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpMeta {
    private int used;                    // number of sends used (includes current send)
    private int max;                     // MAX_RESENDS
    private boolean cooldown;            // true if 5-min cooldown is active
    private boolean resendIntervalLock;  // true if 1-min lock is active
    private int resendIntervalSeconds;   // 60 (info for client)
    private int cooldownSeconds;         // 300 (info for client)
}
