# Comprehensive Logging Implementation Guide

## Overview
The SyncNest User Service now has comprehensive structured logging across all key components. Logs are categorized by component type and include:
- **DEBUG**: Detailed trace information for troubleshooting
- **INFO**: Key business events (login, registration, OTP generation)
- **WARN**: Potential issues requiring attention (failed auth, rate limiting)
- **ERROR**: System failures and exceptions

---

## Logging Configuration

### 1. **Application Properties** (`application.properties`)
Central logging configuration with package-level control:

```properties
# Root logging level (INFO)
logging.level.root=INFO

# Package-specific levels
logging.level.com.syncnest=DEBUG                                      # All SyncNest packages
logging.level.com.syncnest.controller=DEBUG                           # Controller layer
logging.level.com.syncnest.serviceImpl=DEBUG                           # Service implementation
logging.level.com.syncnest.bootstrap=DEBUG                            # Startup bootstrapping
logging.level.org.springframework.security=INFO                       # Spring Security
logging.level.org.springframework.security.web=DEBUG                  # Security filters
logging.level.org.hibernate.SQL=DEBUG                                 # SQL queries

# Log patterns
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg%n
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

### 2. **Logback Configuration** (`logback-spring.xml`)
Advanced rolling file appenders with:
- **CONSOLE**: Real-time console output
- **FILE**: All logs (10MB max, 30 days retention, 1GB total cap)
- **AUTH_FILE**: Auth/OTP/Token logs (60 days retention)
- **AUDIT_FILE**: Device & audit history logs (90 days retention)
- **ERROR_FILE**: Error-only logs (90 days retention)

Spring profiles supported:
- `dev`: DEBUG level with console & file output
- `prod`: INFO level, file-only output (no console)

---

## Logging by Component

### Authentication Flow (`AuthServiceImpl` & `AuthController`)

#### Login Attempt
```
DEBUG: Login attempt for email: a***@gmail.com
DEBUG: Authentication successful for email: a***@gmail.com
DEBUG: Building new user entity for email: a***@gmail.com
INFO: Login success for email: a***@gmail.com, userId: 550e8400-e29b-41d4-a716-446655440000, deviceId: fp-a1b2c3d4
```

#### Failed Login
```
WARN: Authentication failed for email: a***@gmail.com - reason: Bad credentials
WARN: Login rejected for email: a***@gmail.com - account status: enabled=false, locked=true
```

---

### Registration Flow (`RegistrationServiceImpl` & `AuthController`)

#### Successful Registration
```
DEBUG: Registration attempt initiated for email: u***@example.com
DEBUG: Checking if user already registered: u***@example.com
DEBUG: Building new user entity for email: u***@example.com
INFO: User registered successfully: email=u***@example.com, userId: 550e8400-e29b-41d4-a716-446655440001
INFO: Registration initiated for email: u***@example.com, OTP sent
```

#### Validation Error
```
WARN: Registration validation failed: email is null or empty
WARN: Registration rejected: user already exists with email=u***@example.com
```

---

### OTP Flow (`OtpServiceImpl`)

#### OTP Generation
```
DEBUG: OTP generation requested for email: u***@example.com
DEBUG: OTP resend count for email: u***@example.com is now: 1/4
INFO: OTP generated and sent to email: u***@example.com, resend count: 1
```

#### Rate Limiting
```
WARN: OTP generation rejected for email: u***@example.com - cooldown active
WARN: OTP generation rejected for email: u***@example.com - resend interval active
WARN: OTP quota exceeded for email: u***@example.com - resend count: 5, starting cooldown
```

#### OTP Verification
```
DEBUG: OTP verification attempted for email: u***@example.com
DEBUG: OTP verified successfully for email: u***@example.com
INFO: OTP verification successful for email: u***@example.com, userId: 550e8400-e29b-41d4-a716-446655440001
```

#### OTP Errors
```
WARN: OTP verification failed for email: u***@example.com - OTP missing or expired
WARN: OTP verification failed for email: u***@example.com - incorrect OTP, attempts: 2/5
ERROR: OTP max attempts reached for email: u***@example.com, starting cooldown
```

---

### Device Tracking (`DeviceMetadataServiceImpl`)

#### New Device Registration
```
DEBUG: Async device metadata upsert started for user=u***@example.com, ip=192.168.1.100, ua=Mozilla/5.0...
DEBUG: Resolving location for IP: 192.168.1.100
DEBUG: Inserting new device metadata for user=u***@example.com, fingerprint=fp-d4c3b2a1, os=Windows 10, browser=Chrome
DEBUG: Location resolved for IP: 192.168.1.100 -> New York, NY, United States
INFO: Registered new device for user=u***@example.com fingerprint=fp-d4c3b2a1 os=Windows 10 browser=Chrome location=New York, NY, United States
```

#### Device Update
```
DEBUG: Updating existing device metadata: deviceId=fp-d4c3b2a1, newIp=192.168.1.101, newLocation=San Francisco, CA, United States
INFO: Updated device metadata id=42, user=u***@example.com
```

---

### Refresh Token Flow (`RefreshTokenServiceImpl`)

#### Token Issuance
```
DEBUG: Issuing refresh token for user=u***@example.com, sessionId=sess-abc123
DEBUG: Refresh token saved: tokenId=550e8400-e29b-41d4-a716-446655440002, user=u***@example.com, expiresAt=2026-03-24T14:30:00Z
INFO: Refresh token issued for user=u***@example.com, sessionId=sess-abc123, expiresAt=2026-03-24T14:30:00Z
```

#### Token Rotation
```
DEBUG: Validating refresh token for deviceId: null
DEBUG: Refresh token found for user=u***@example.com, sessionId=sess-abc123, expiresAt=2026-03-24T14:30:00Z
DEBUG: Refresh token revoked for user=u***@example.com, sessionId=sess-abc123
INFO: Refresh token rotated successfully for user=u***@example.com, newExpiresAt=2026-03-25T14:30:00Z
```

#### Token Errors
```
WARN: Refresh token validation failed: token invalid or expired
WARN: Refresh token device mismatch for user=u***@example.com: expected=sess-abc123, provided=sess-xyz789
```

---

### Logout Flow

#### Logout Success
```
DEBUG: Logout request received from authenticated user: u***@example.com
INFO: Revoked all refresh tokens for user=u***@example.com
INFO: Logout successful for user: u***@example.com
```

#### Device-Specific Logout
```
INFO: Revoked refresh tokens for user=u***@example.com deviceId=sess-abc123
```

---

### Bootstrap/Initialization (`UserInitializer`)

#### Successful Bootstrap
```
INFO: RefreshTokenService initialized: maxDevices=3, inactivitySeconds=604800, absoluteLifetimeMs=2592000000, randomBytes=64
INFO: Admin 'admin@example.com' with profile added successfully.
INFO: Regular 'user@example.com' with profile added successfully.
INFO: Bootstrap users already exist. Skipping startup seed data.
```

---

## Log File Locations

Default log file paths (configurable via `logging.file.name` or `LOG_PATH`):
- **All logs**: `/tmp/spring.log` (or `${LOG_PATH}/spring.log`)
- **Auth logs**: `/tmp/auth.log` (failed logins, OTP issues)
- **Audit logs**: `/tmp/audit.log` (device tracking, audit events)
- **Error logs**: `/tmp/error.log` (exceptions only)

### Rotation Policy
- **Max file size**: 10MB (rolls to `.gz`)
- **Max history**: 30-90 days (depending on file type)
- **Total size cap**: 500MB-1GB (oldest files deleted when exceeded)

---

## Email Masking Strategy

All logs mask user emails for privacy/security:
- **Full email**: `user@example.com` → `u***@example.com`
- **Short emails**: `ab@x.com` → `***@***`
- Used in all INFO, DEBUG, WARN levels to comply with data privacy regulations

---

## Performance Impact

### Async Logging
- **Device metadata upsert**: Async (`@Async`) to prevent auth flow blocking
- **Audit history**: Best-effort, never blocks auth

### Ring Buffer (Logback)
- Console appender uses synchronous handler for real-time feedback
- File appenders use async handlers via `AsyncAppender` for performance

---

## Troubleshooting

### Enable Maximum Verbosity (Development)
```properties
logging.level.root=DEBUG
logging.level.org.springframework=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

### Check OTP Issues
```bash
tail -f /tmp/auth.log | grep OTP
```

### Monitor Failed Login Attempts
```bash
tail -f /tmp/auth.log | grep "Authentication failed"
```

### Track Device Registrations
```bash
tail -f /tmp/audit.log | grep "Registered new device"
```

### View All Errors
```bash
tail -f /tmp/error.log
```

---

## Best Practices

1. **Never log passwords or tokens** ✓ (Already implemented)
2. **Use DEBUG for detailed flow tracing** ✓
3. **Use INFO for business-critical events** ✓
4. **Use WARN for potential issues** ✓
5. **Use ERROR for system failures** ✓
6. **Mask PII (emails, IPs) in INFO logs** ✓
7. **Keep separate logs for audit compliance** ✓
8. **Rotate logs regularly** ✓ (Logback rolling policy)
9. **Monitor error.log for production issues** ✓

---

## Future Enhancements

Potential improvements:
- [ ] Structured logging (JSON format) for log aggregation
- [ ] Centralized logging (ELK stack / Splunk integration)
- [ ] Real-time alerting on suspicious patterns
- [ ] Log retention policies per compliance requirements
- [ ] Performance metrics logging (response times, DB queries)
- [ ] Distributed tracing (OpenTelemetry integration)

