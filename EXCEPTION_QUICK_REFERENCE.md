# Exception Handling Quick Reference

## 🎯 Error Code Patterns

### Error Code Format
```
[DOMAIN]_[NUMBER]
Example: AUTH_001, OTP_005, TOKEN_003
```

### Quick Domain Lookup
| What Failed | Domain | Example |
|-------------|--------|---------|
| Login/JWT | AUTH_* | AUTH_001 |
| Sign up | REG_* | REG_001 |
| OTP | OTP_* | OTP_005 |
| Session/Token | TOKEN_* | TOKEN_002 |
| User | USER_* | USER_001 |
| Device | DEVICE_* | DEVICE_001 |
| Input | VALIDATION_* | VALIDATION_003 |
| External Service | EXTERNAL_* | EXTERNAL_001 |
| System | SYSTEM_* | SYSTEM_001 |

---

## 📋 Most Common Errors

```java
// Authentication (401 Unauthorized)
new AuthExceptions.InvalidCredentials(detail)          // AUTH_001
new AuthExceptions.AccountSuspended(detail)            // AUTH_002
new AuthExceptions.JwtExpired(detail)                  // AUTH_005
new AuthExceptions.JwtInvalid(detail)                  // AUTH_006

// Registration (409 Conflict)
new UserExceptions.UserAlreadyExists(email)            // USER_002
new RequestExceptions.MissingRequiredField("field")    // VALIDATION_003

// OTP (429 Too Many Requests)
new OtpExceptions.MaxOtpAttemptsExceeded(detail)      // OTP_004
new OtpExceptions.OtpCooldownActive(detail)           // OTP_005
new OtpExceptions.OtpIncorrect(detail)                // OTP_003

// Tokens (401 Unauthorized)
new TokenExceptions.RefreshTokenExpired(detail)        // TOKEN_002
new TokenExceptions.DeviceMismatch(detail)             // TOKEN_004

// Validation (400 Bad Request)
new RequestExceptions.ValidationFailed(detail)         // VALIDATION_001
new RequestExceptions.InvalidParameter(detail)         // VALIDATION_002

// Rate Limiting (429 Too Many Requests)
new RequestExceptions.RateLimited(detail)              // SYSTEM_008
```

---

## 📊 HTTP Status Code Quick Reference

| Status | When to Use | Examples |
|--------|------------|----------|
| **400** | Bad request data | Missing fields, invalid format |
| **401** | Authentication failed | Wrong password, expired JWT |
| **403** | No permission | Insufficient access |
| **404** | Not found | User doesn't exist |
| **409** | Conflict | Email already registered |
| **412** | Precondition failed | Email not verified |
| **423** | Resource locked | Account suspended |
| **429** | Too many requests | OTP cooldown, rate limited |
| **500** | Server error | Database error, crash |
| **502** | Bad gateway | External service error |
| **503** | Unavailable | Service down, maintenance |
| **504** | Gateway timeout | External service slow |

---

## 💻 Response Example

All failures return this structure:

```json
{
  "type": "https://syncnest.dev/problems/[slug]",
  "title": "Human Readable Title",
  "detail": "Specific failure reason",
  "errorCode": "DOMAIN_NNN",
  "instance": "/api/v1/endpoint",
  "timestamp": "2026-03-17T14:30:45.123Z",
  "status": 401
}
```

### Example: Login Failed
```json
{
  "type": "https://syncnest.dev/problems/invalid-credentials",
  "title": "Invalid Credentials",
  "detail": "Email or password is incorrect",
  "errorCode": "AUTH_001",
  "instance": "/api/v1/auth/login",
  "timestamp": "2026-03-17T14:30:45.123Z",
  "status": 401
}
```

---

## 🚀 Common Flows

### Login Flow Errors
1. **Email/Password wrong** → AUTH_001 (401)
2. **Account not verified** → AUTH_003 (412)
3. **Account suspended** → AUTH_002 (423)
4. **User not found** → AUTH_010 (404)

### Registration Flow Errors
1. **Email already exists** → REG_001 / USER_002 (409)
2. **Invalid email format** → REG_002 (400)
3. **Password too weak** → REG_003 (400)
4. **Required field missing** → VALIDATION_003 (400)

### OTP Flow Errors
1. **OTP expired** → OTP_001 (400)
2. **OTP incorrect** → OTP_003 (400)
3. **Max attempts** → OTP_004 (429)
4. **Cooldown active** → OTP_005 (429)
5. **Rate limited** → OTP_006 (429)

### Token Refresh Errors
1. **Token expired** → TOKEN_002 (401)
2. **Token invalid** → TOKEN_001 (401)
3. **Device mismatch** → TOKEN_004 (401)
4. **No sessions** → TOKEN_005 (404)

---

## 🔍 Error Code Meanings

### AUTH_ (Authentication)
- **001**: Invalid email or password combination
- **002**: Account suspended/disabled by admin
- **003**: Email not verified - needs verification
- **005**: JWT expired - need new access token
- **006**: JWT malformed/invalid - unauthorized

### OTP_ (One-Time Password)
- **001**: OTP time-limited, request new one
- **003**: Digits entered don't match sent OTP
- **004**: Too many wrong attempts, wait 5 min
- **005**: Just failed max times, cooldown active
- **006**: Trying to request too frequently

### TOKEN_ (Refresh & Sessions)
- **001**: Invalid refresh token signature
- **002**: Refresh token expired - login again
- **004**: Token from different device detected
- **007**: Too many active sessions/devices

### REG_ (Registration)
- **001**: Email already has account
- **002**: Email format invalid
- **003**: Password doesn't meet requirements
- **010**: Email verification required first

### VALIDATION_ (Input)
- **001**: Generic validation error
- **002**: Parameter invalid/missing
- **003**: Required field not provided
- **005**: Value too long
- **006**: Not a valid enum value

---

## ✅ Implementation Checklist

- [x] 100+ error codes defined
- [x] Machine-readable error codes in responses
- [x] Detailed failure reasons for all errors
- [x] Proper HTTP status codes
- [x] RFC 7807 Problem Details format
- [x] Request IDs and timestamps
- [x] Exception classes organized by domain
- [x] Error response formatting utilities
- [x] GlobalExceptionHandler updated
- [x] Complete documentation

---

## 📖 For More Details

See: `EXCEPTION_HANDLING_GUIDE.md`

- Complete error code reference
- Example responses for each category
- Usage examples for developers
- Testing commands with curl
- Benefits and design patterns

