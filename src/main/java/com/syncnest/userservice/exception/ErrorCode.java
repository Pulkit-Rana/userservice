package com.syncnest.userservice.exception;

/**
 * Centralized error codes for all API failures.
 * Format: <DOMAIN>_<SEVERITY>_<NUMBER>
 * Example: AUTH_001, REG_001, OTP_001, etc.
 *
 * Domains:
 *  - AUTH_*: Authentication/JWT related
 *  - REG_*: Registration related
 *  - OTP_*: OTP related
 *  - TOKEN_*: Refresh token related
 *  - USER_*: User management
 *  - DEVICE_*: Device tracking
 *  - VALIDATION_*: Input validation
 *  - EXTERNAL_*: Third-party services
 *  - SYSTEM_*: Internal system errors
 *  - PWD_*: Forgot/reset password flow
 */
public enum ErrorCode {

    // =============== AUTHENTICATION ERRORS (AUTH_*) ===============
    AUTH_001("AUTH_001", "Invalid credentials provided", "Email or password is incorrect"),
    AUTH_002("AUTH_002", "Account suspended", "Your account has been suspended or disabled"),
    AUTH_003("AUTH_003", "Account not verified", "Please verify your email address first"),
    AUTH_004("AUTH_004", "Email not found", "No account found with provided email"),
    AUTH_005("AUTH_005", "JWT expired", "Your session has expired. Please login again"),
    AUTH_006("AUTH_006", "JWT invalid", "Invalid or malformed authentication token"),
    AUTH_007("AUTH_007", "JWT blacklisted", "This token has been revoked"),
    AUTH_008("AUTH_008", "No authorization header", "Authorization header is missing"),
    AUTH_009("AUTH_009", "Bearer token missing", "Invalid Authorization header format"),
    AUTH_010("AUTH_010", "User no longer exists", "User account has been deleted"),

    // =============== REGISTRATION ERRORS (REG_*) ===============
    REG_001("REG_001", "Email already exists", "An account with this email already exists"),
    REG_002("REG_002", "Invalid email format", "Please provide a valid email address"),
    REG_003("REG_003", "Password too weak", "Password must be at least 8 characters with uppercase, lowercase, number, and symbol"),
    REG_004("REG_004", "Email required", "Email address is mandatory"),
    REG_005("REG_005", "Password required", "Password is mandatory"),
    REG_006("REG_006", "First name required", "First name is mandatory"),
    REG_007("REG_007", "Last name required", "Last name is mandatory"),
    REG_008("REG_008", "Terms not accepted", "You must accept terms and conditions"),
    REG_009("REG_009", "Registration failed", "Unable to create account. Please try again"),
    REG_010("REG_010", "Email verification pending", "Please verify your email before proceeding"),

    // =============== OTP ERRORS (OTP_*) ===============
    OTP_001("OTP_001", "OTP expired", "OTP has expired. Please request a new one"),
    OTP_002("OTP_002", "OTP invalid", "Invalid OTP provided. Please try again"),
    OTP_003("OTP_003", "OTP incorrect", "Incorrect OTP. Please check and try again"),
    OTP_004("OTP_004", "Max OTP attempts exceeded", "Too many incorrect attempts. Please request a new OTP after 5 minutes"),
    OTP_005("OTP_005", "OTP cooldown active", "Please wait 5 minutes before requesting another OTP"),
    OTP_006("OTP_006", "OTP resend rate limited", "Please wait 1 minute before requesting another OTP"),
    OTP_007("OTP_007", "OTP quota exceeded", "Too many OTP requests. Please try again after 5 minutes"),
    OTP_008("OTP_008", "OTP not found", "No OTP found for this email. Please request a new one"),
    OTP_009("OTP_009", "OTP generation failed", "Unable to generate OTP. Please try again"),
    OTP_010("OTP_010", "OTP delivery failed", "Failed to send OTP to email. Please verify email address"),

    // =============== REFRESH TOKEN ERRORS (TOKEN_*) ===============
    TOKEN_001("TOKEN_001", "Refresh token invalid", "Refresh token is invalid or expired"),
    TOKEN_002("TOKEN_002", "Refresh token expired", "Your refresh token has expired. Please login again"),
    TOKEN_003("TOKEN_003", "Refresh token revoked", "This refresh token has been revoked"),
    TOKEN_004("TOKEN_004", "Device mismatch", "This token is not valid for the current device"),
    TOKEN_005("TOKEN_005", "No active sessions", "No active sessions found for your account"),
    TOKEN_006("TOKEN_006", "Session expired", "Your session has expired. Please login again"),
    TOKEN_007("TOKEN_007", "Max sessions exceeded", "Maximum concurrent sessions limit reached"),
    TOKEN_008("TOKEN_008", "Token hashing failed", "Internal error: Unable to process token"),
    TOKEN_009("TOKEN_009", "Session invalid", "Your session is invalid. Please login again"),
    TOKEN_010("TOKEN_010", "Refresh token required", "Refresh token is required for this operation"),
    TOKEN_011("TOKEN_011", "Replay attack detected", "Suspicious token reuse detected. All sessions revoked for safety"),

    // =============== USER ERRORS (USER_*) ===============
    USER_001("USER_001", "User not found", "User account not found"),
    USER_002("USER_002", "User already exists", "User account already exists with this identifier"),
    USER_003("USER_003", "User inactive", "User account is inactive or disabled"),
    USER_004("USER_004", "User locked", "User account is locked. Please contact support"),
    USER_005("USER_005", "User precondition failed", "Required user preconditions are not met"),
    USER_006("USER_006", "User update conflict", "Cannot update user. Conflicting data detected"),
    USER_007("USER_007", "Invalid user input", "Provided user data is invalid"),
    USER_008("USER_008", "Email not verified", "Email address has not been verified"),
    USER_009("USER_009", "Profile not found", "User profile not found"),
    USER_010("USER_010", "User deletion failed", "Unable to delete user account"),
    USER_011("USER_011", "Account already deleted", "This account has already been deleted"),
    USER_012("USER_012", "Restore grace period expired", "The restoration window has expired and the account can no longer be recovered"),
    USER_013("USER_013", "Restore failed", "Unable to restore user account"),

    // =============== DEVICE ERRORS (DEVICE_*) ===============
    DEVICE_001("DEVICE_001", "Device not found", "Device not found in your account"),
    DEVICE_002("DEVICE_002", "Device registration failed", "Failed to register device"),
    DEVICE_003("DEVICE_003", "Device limit exceeded", "Maximum number of registered devices exceeded"),
    DEVICE_004("DEVICE_004", "Device metadata missing", "Device metadata is incomplete"),
    DEVICE_005("DEVICE_005", "Geolocation failed", "Unable to determine device location"),
    DEVICE_006("DEVICE_006", "Invalid device data", "Provided device data is invalid"),
    DEVICE_007("DEVICE_007", "Device deactivation failed", "Failed to deactivate device"),
    DEVICE_008("DEVICE_008", "Device already exists", "This device is already registered"),
    DEVICE_009("DEVICE_009", "Device IP tracking failed", "Failed to track device IP address"),
    DEVICE_010("DEVICE_010", "Device fingerprint invalid", "Device fingerprint is invalid or corrupted"),

    // =============== VALIDATION ERRORS (VALIDATION_*) ===============
    VALIDATION_001("VALIDATION_001", "Validation failed", "Request validation failed"),
    VALIDATION_002("VALIDATION_002", "Invalid parameter", "One or more parameters are invalid"),
    VALIDATION_003("VALIDATION_003", "Missing required field", "One or more required fields are missing"),
    VALIDATION_004("VALIDATION_004", "Invalid field format", "Field format is invalid"),
    VALIDATION_005("VALIDATION_005", "Field length exceeded", "Field value exceeds maximum length"),
    VALIDATION_006("VALIDATION_006", "Invalid enum value", "Provided value is not a valid option"),
    VALIDATION_007("VALIDATION_007", "Date validation failed", "Date format or value is invalid"),
    VALIDATION_008("VALIDATION_008", "Constraint violation", "Request violates validation constraints"),
    VALIDATION_009("VALIDATION_009", "Type mismatch", "Parameter type does not match expected type"),
    VALIDATION_010("VALIDATION_010", "Business rule violation", "Request violates business rules"),

    // =============== EXTERNAL SERVICE ERRORS (EXTERNAL_*) ===============
    EXTERNAL_001("EXTERNAL_001", "Email service unavailable", "Email service is currently unavailable"),
    EXTERNAL_002("EXTERNAL_002", "Email delivery failed", "Failed to deliver email. Please try again"),
    EXTERNAL_003("EXTERNAL_003", "Upstream service unavailable", "Third-party service is unavailable"),
    EXTERNAL_004("EXTERNAL_004", "Upstream timeout", "Third-party service did not respond in time"),
    EXTERNAL_005("EXTERNAL_005", "Upstream error", "Third-party service returned an error"),
    EXTERNAL_006("EXTERNAL_006", "Geolocation service error", "Unable to determine location from IP"),
    EXTERNAL_007("EXTERNAL_007", "Payment gateway error", "Payment processing failed"),
    EXTERNAL_008("EXTERNAL_008", "SMS service error", "Failed to send SMS"),
    EXTERNAL_009("EXTERNAL_009", "External API error", "Error communicating with external service"),
    EXTERNAL_010("EXTERNAL_010", "Service quota exceeded", "External service quota has been exceeded"),

    // =============== SYSTEM ERRORS (SYSTEM_*) ===============
    SYSTEM_001("SYSTEM_001", "Internal server error", "An unexpected error occurred. Please contact support"),
    SYSTEM_002("SYSTEM_002", "Database error", "Database operation failed"),
    SYSTEM_003("SYSTEM_003", "Configuration error", "System configuration is invalid"),
    SYSTEM_004("SYSTEM_004", "Resource not found", "Requested resource not found"),
    SYSTEM_005("SYSTEM_005", "Method not allowed", "HTTP method not allowed for this endpoint"),
    SYSTEM_006("SYSTEM_006", "Unsupported media type", "Request content-type is not supported"),
    SYSTEM_007("SYSTEM_007", "Bad request", "Request is malformed or invalid"),
    SYSTEM_008("SYSTEM_008", "Too many requests", "Rate limit exceeded. Please try again later"),
    SYSTEM_009("SYSTEM_009", "Service unavailable", "Service is temporarily unavailable"),
    SYSTEM_010("SYSTEM_010", "Conflict", "Request conflicts with existing data"),

    // =============== PASSWORD RESET ERRORS (PWD_*) ===============
    PWD_001("PWD_001", "Reset code invalid", "Reset code is invalid or does not match"),
    PWD_002("PWD_002", "Reset code expired", "Reset code has expired. Please request a new one"),
    PWD_003("PWD_003", "Too many reset attempts", "Too many invalid reset attempts. Please try again later"),
    PWD_004("PWD_004", "Reset request rate limited", "Please wait before requesting another reset code"),
    PWD_005("PWD_005", "Password reset failed", "Unable to reset password at this time");

    // =============== Fields ===============

    private final String code;
    private final String title;
    private final String message;

    ErrorCode(String code, String title, String message) {
        this.code = code;
        this.title = title;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Get error code by code string
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode ec : values()) {
            if (ec.code.equals(code)) {
                return ec;
            }
        }
        return SYSTEM_001; // Default to internal error
    }
}

