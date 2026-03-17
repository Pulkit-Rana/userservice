# UserService

A production-ready **user authentication and management microservice** built with **Spring Boot 3.5.4** for the **SyncNest** platform. It handles everything from user registration and email-OTP verification to JWT-based login, refresh token rotation, multi-device session tracking, and OAuth2 (Google) integration.

---

## Table of Contents

1. [What This Service Does](#what-this-service-does)
2. [Project Structure](#project-structure)
3. [Technologies Used](#technologies-used)
4. [Key Components](#key-components)
5. [API Endpoints](#api-endpoints)
6. [Authentication Flow](#authentication-flow)
7. [Security Features](#security-features)
8. [Database Schema](#database-schema)
9. [Configuration](#configuration)
10. [Getting Started](#getting-started)

---

## What This Service Does

UserService is a standalone microservice that manages the **identity and session lifecycle** of users in the SyncNest platform:

- **User Registration** — Collect user details, send a 6-digit OTP to the provided email, and activate the account only after successful OTP verification.
- **Login** — Validate credentials, issue a short-lived JWT access token (15 min) and a long-lived refresh token (30 days, stored in an HttpOnly cookie).
- **Token Rotation** — When the access token expires, exchange the refresh token for a brand-new access token + refresh token pair.
- **Logout** — Revoke refresh tokens for a single device or all devices at once; blacklist the access token in Redis.
- **Multi-Device Session Tracking** — Each login records device metadata (device ID, type, location, provider). Users can manage or revoke individual device sessions.
- **OTP Management** — Rate-limited (max 3 sends, 60 s between sends, 5-minute cooldown after 3 failed attempts), with OTPs hashed in Redis.
- **Role-Based Access Control** — `ROLE_ADMIN` and `ROLE_USER` roles enforced at the endpoint level.
- **OAuth2 (Google)** — Integration scaffolding is in place; full implementation is pending.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/syncnest/userservice/
│   │   ├── UserserviceApplication.java           # Spring Boot entry point
│   │   ├── bootstrap/
│   │   │   └── UserInitializer.java              # Seeds default admin & user on startup
│   │   ├── controller/
│   │   │   ├── AuthController.java               # Login, register, OTP, logout, token refresh
│   │   │   └── OAuthController.java              # OAuth2 scaffold (Google)
│   │   ├── service/                              # Service interfaces
│   │   │   ├── AuthService.java
│   │   │   ├── RegistrationService.java
│   │   │   ├── RefreshTokenService.java
│   │   │   └── OtpService.java
│   │   ├── serviceImpl/                          # Service implementations
│   │   │   ├── AuthServiceImpl.java
│   │   │   ├── RegistrationServiceImpl.java
│   │   │   ├── RefreshTokenServiceImpl.java
│   │   │   ├── OtpServiceImpl.java
│   │   │   └── UserServiceImpl.java
│   │   ├── entity/
│   │   │   ├── User.java                         # Implements UserDetails; core user record
│   │   │   ├── Profile.java                      # 1:1 user profile details
│   │   │   ├── RefreshToken.java                 # Hashed refresh token storage
│   │   │   ├── DeviceMetadata.java               # Per-device session info
│   │   │   ├── BaseEntity.java                   # UUID PK + JPA audit fields
│   │   │   ├── UserRole.java                     # Enum: ROLE_ADMIN, ROLE_USER
│   │   │   ├── AuthProvider.java                 # Enum: LOCAL, GOOGLE
│   │   │   └── DeviceType.java                   # Enum: DESKTOP, MOBILE, TABLET, UNKNOWN
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── RefreshTokenRepository.java
│   │   │   └── DeviceMetadataRepository.java
│   │   ├── SecurityConfig/
│   │   │   ├── SecurityConfig.java               # Spring Security filter chain
│   │   │   ├── JwtTokenProviderConfig.java        # JWT creation & validation (HS256)
│   │   │   ├── JwtAuthFilterConfig.java           # JWT request filter
│   │   │   ├── JwtAuthenticationEntryPoint.java   # 401 handler
│   │   │   ├── JwtAccessDeniedHandler.java        # 403 handler
│   │   │   └── TokenBlacklistConfig.java          # Redis-backed token blacklist
│   │   ├── config/
│   │   │   ├── RedisConfig.java                  # Lettuce Redis connection
│   │   │   ├── CacheConfig.java                  # Caffeine in-memory cache
│   │   │   ├── MailConfig.java                   # Gmail SMTP setup
│   │   │   ├── OpenApiConfig.java                # Swagger / OpenAPI docs
│   │   │   ├── GlobalResponseAdvice.java         # Wraps all responses in ApiResponse
│   │   │   └── ResponseConfig.java               # Response formatting helpers
│   │   ├── dto/                                  # Request / Response DTOs
│   │   ├── exception/                            # Custom exceptions + GlobalExceptionHandler
│   │   ├── Validators/                           # @PasswordMatch custom annotation
│   │   └── utils/                               # Email templates, device detection, auditor
│   └── resources/
│       └── application.properties               # All runtime configuration
└── test/
    └── java/com/syncnest/userservice/
        └── UserserviceApplicationTests.java
```

---

## Technologies Used

| Category | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.5.4 |
| Security | Spring Security + OAuth2 Client | 6.5.2 |
| Database | Spring Data JPA / Hibernate + MySQL | Latest (via Boot) |
| Cache | Spring Cache + Caffeine | Latest |
| Session Store | Spring Data Redis (Lettuce) | Latest |
| JWT | JJWT | 0.12.6 |
| Email | Spring Mail (Gmail SMTP) | 3.5.0 |
| API Docs | SpringDoc OpenAPI (Swagger UI) | 2.1.0 |
| Validation | Jakarta Validation + Hibernate Validator | 8.0.1 |
| Logging | Logback + Logstash Encoder | 7.4 |
| Metrics | Micrometer (Prometheus) | Latest |
| Utilities | Lombok, ModelMapper, Apache Commons | Latest |
| Build | Gradle (wrapper included) | — |

---

## Key Components

### Controllers

#### `AuthController` — base path `/api/v1/auth/auth`

Handles all authentication operations: login, registration, OTP verification, token refresh, and logout.

#### `OAuthController`

Scaffold for OAuth2 authentication (Google). Full implementation is pending.

---

### Services

#### `AuthServiceImpl`
- Validates credentials via Spring Security `AuthenticationManager`.
- Checks `isLocked` / `enabled` status before issuing tokens.
- Issues a signed JWT (15-minute expiry) and a hashed refresh token.
- Records device metadata on every login.

#### `RegistrationServiceImpl`
- Normalises and deduplicates email addresses.
- Creates a `User` (locked, unverified) + linked `Profile` and initial `DeviceMetadata`.
- Encodes passwords with BCrypt (strength 12).
- Triggers OTP generation after saving the user.

#### `OtpServiceImpl`
- Generates a 6-digit OTP, stores its SHA-256 hash in Redis with a 1-minute TTL.
- Sends the OTP to the user's email via Gmail SMTP.
- Enforces rate limits: max 3 sends, 60 s resend interval, 5-minute cooldown on 3 failures.
- On successful verification, unlocks and verifies the user account.

#### `RefreshTokenServiceImpl`
- Stores refresh tokens as SHA-256 hashes in MySQL.
- Rotates the token on every refresh (old token revoked, new token issued).
- Caps active tokens at 3 per user — oldest is revoked automatically.
- Supports per-device or global revocation (logout).

---

### Security

| Component | Responsibility |
|---|---|
| `SecurityConfig` | Stateless session, CORS, route-level authorization rules, security headers |
| `JwtTokenProviderConfig` | HS256 JWT signing & parsing, 30 s clock-skew tolerance |
| `JwtAuthFilterConfig` | Extracts `Bearer` token from `Authorization` header, populates `SecurityContext` |
| `JwtAuthenticationEntryPoint` | Returns 401 JSON response for unauthenticated requests |
| `JwtAccessDeniedHandler` | Returns 403 JSON response for unauthorized requests |
| `TokenBlacklistConfig` | Stores revoked JWTs in Redis until their natural expiry |

---

### Exception Handling

`GlobalExceptionHandler` maps exceptions to RFC-7807-style JSON error responses:

| Exception | HTTP Status |
|---|---|
| `ApiException` (custom hierarchy) | Defined per exception |
| Validation failure | 422 Unprocessable Entity |
| DB constraint violation | 409 Conflict |
| Malformed request body | 400 Bad Request |
| Method not allowed | 405 Method Not Allowed |
| No handler found | 404 Not Found |
| Unhandled exception | 500 Internal Server Error |

---

## API Endpoints

All endpoints are prefixed with `/api/v1/auth/auth`.

### `GET /ping`
Health-check. Returns `"pong"`.

---

### `POST /register`
Register a new user account.

**Request body:**
```json
{
  "email": "jane@example.com",
  "password": "SecurePass123",
  "confirmPassword": "SecurePass123",
  "firstName": "Jane",
  "lastName": "Smith"
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Registration initiated. An OTP has been sent to your email. Verify within 1 minute.",
  "email": "jane@example.com",
  "otpMeta": {
    "used": 1,
    "max": 3,
    "cooldown": false,
    "resendIntervalLock": false,
    "resendIntervalSeconds": 60,
    "cooldownSeconds": 300
  }
}
```
`Location` header → `/api/v1/auth/verify-otp`

---

### `POST /verify-otp`
Verify the OTP and activate the account. Issues tokens on success.

**Request body:**
```json
{
  "email": "jane@example.com",
  "otp": "482910"
}
```

**Response `200 OK`:**
```json
{
  "accessToken": "eyJhbGc...",
  "expiresIn": 900000,
  "deviceId": "<auto-generated>",
  "issuedAt": "2024-01-01T12:00:00Z",
  "user": { "email": "jane@example.com", "firstName": "Jane", "lastName": "Smith", "role": "ROLE_USER", "verified": true }
}
```
`Set-Cookie: refreshToken=<token>; HttpOnly; Secure; SameSite=Strict; Max-Age=2592000`

---

### `POST /login`
Authenticate an existing user.

**Request body:**
```json
{
  "email": "jane@example.com",
  "password": "SecurePass123",
  "deviceId": "device-abc",
  "clientId": "client-xyz",
  "location": "New York",
  "provider": "LOCAL",
  "deviceType": "DESKTOP"
}
```

**Response `200 OK`:** Same shape as `/verify-otp`.
`Set-Cookie: refreshToken=<token>; HttpOnly; Secure; SameSite=Strict; Max-Age=2592000`

---

### `POST /refreshToken`
Exchange an existing refresh token for a new access token + refresh token pair.

**Request body:**
```json
{ "refreshToken": "<value-from-cookie>" }
```

**Response `200 OK`:**
```json
{
  "accessToken": "eyJhbGc...",
  "expiresIn": 900000,
  "expiresAt": "2024-01-01T13:00:00Z"
}
```
New `Set-Cookie` header with rotated refresh token.

---

### `POST /logout`
Revoke tokens and clear the cookie. Pass `deviceId` to log out a single device; omit it to log out all devices.

**Request body:**
```json
{ "deviceId": "device-abc" }
```

**Response `200 OK`:**
```json
{ "success": true, "message": "Logged out" }
```
`Set-Cookie: refreshToken=; Max-Age=0`

---

## Authentication Flow

```
1.  POST /register      → account created (locked/unverified)
          │
          └─► OTP sent to email
                    │
2.  POST /verify-otp  → account unlocked + verified
                    │
                    └─► JWT access token + refresh token issued
                                │
3.  Use JWT in `Authorization: Bearer <token>` for all secured requests
                                │
4.  Token expires (15 min) ──► POST /refreshToken → new token pair
                                │
5.  POST /logout       → tokens revoked, cookie cleared
```

---

## Security Features

| Area | Implementation |
|---|---|
| **Passwords** | BCrypt hashing, strength 12, 8-character minimum |
| **JWT** | HS256, 15-minute expiry, 30 s clock skew |
| **Refresh tokens** | SHA-256 hashed in DB, 30-day expiry, max 3 active per user |
| **OTP** | SHA-256 hashed in Redis, 1-minute TTL, rate-limited |
| **Cookies** | `HttpOnly`, `Secure`, `SameSite=Strict` |
| **HTTP headers** | HSTS (365 days), CSP (`default-src 'none'`), `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` |
| **CORS** | Strict origin validation (default: `http://localhost:3000`) |
| **Session** | Fully stateless (JWT); no server-side session |
| **Logout** | Access token blacklisted in Redis; refresh tokens revoked in DB |
| **Error messages** | Generic messages for auth failures to prevent user enumeration |

---

## Database Schema

The service uses **MySQL** with Hibernate auto-DDL (`update`).

```sql
-- core user record
users (id UUID PK, email VARCHAR(50) UNIQUE, password, role, is_locked, is_verified, enabled, is_deleted, created_at, updated_at, created_by, updated_by)

-- extended user profile (1:1)
profiles (id UUID PK, user_id UUID UNIQUE FK, first_name, last_name, phone_number, address, city, country, zip_code, profile_picture_url, created_at, updated_at)

-- hashed refresh tokens
refresh_tokens (id BIGINT PK, token_hash VARCHAR(64) UNIQUE, user_id UUID FK, device_id, issued_at, expires_at, revoked BOOLEAN)

-- per-device session metadata
device_metadata (meta_id BIGINT PK, user_id UUID FK, location, provider, device_type, last_login_at)
```

---

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Default | Purpose |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/sync_nest` | MySQL connection |
| `spring.datasource.username` | `root` | MySQL user |
| `spring.datasource.password` | `12345` | MySQL password |
| `token.key.secret` | *(Base64-encoded 256-bit key)* | JWT signing secret |
| `token.key.jwtExpiration` | `900000` | JWT expiry in ms (15 min) |
| `refresh-token.expiration.milliseconds` | `2592000000` | Refresh token expiry (30 days) |
| `refresh-token.max.count` | `3` | Max concurrent refresh tokens per user |
| `spring.data.redis.host` | `127.0.0.1` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `spring.mail.username` | *(Gmail address)* | Email sender |
| `spring.mail.password` | *(App password)* | Gmail SMTP credential |
| `app.init.admin.password` | `adminPassword123` | Seeded admin password |
| `app.init.user.password` | `userPassword123` | Seeded user password |

> **Important:** Replace all default credentials (database password, JWT secret, mail password) before deploying to any non-local environment.

---

## Getting Started

### Prerequisites

- JDK 17+
- MySQL 5.7+ (database: `sync_nest`)
- Redis 6.0+

### 1. Create the database

```sql
CREATE DATABASE sync_nest;
```

### 2. Start Redis

```bash
# Docker
docker run -d -p 6379:6379 redis:latest

# or local
redis-server
```

### 3. Configure the application

Edit `src/main/resources/application.properties` and set your MySQL credentials, Redis address, JWT secret, and mail credentials.

### 4. Build

```bash
./gradlew clean build
```

### 5. Run

```bash
./gradlew bootRun
# or
java -jar build/libs/userservice-0.0.1-SNAPSHOT.jar
```

The service starts at `http://localhost:8080/api/v1/auth/`.
Swagger UI is available at `http://localhost:8080/api/v1/auth/swagger-ui.html`.

### 6. Test

```bash
./gradlew test
```

### Default Seed Accounts (created on first startup)

| Email | Password | Role |
|---|---|---|
| `admin@example.com` | `adminPassword123` | ROLE_ADMIN |
| `user@example.com` | `userPassword123` | ROLE_USER |
