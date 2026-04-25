package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.dto.GoogleLoginRequest;
import com.syncnest.userservice.dto.GoogleProfileSnapshot;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.entity.AuditEventType;
import com.syncnest.userservice.entity.AuditOutcome;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.entity.Profile;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.entity.UserRole;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import com.syncnest.userservice.service.AuditHistoryService;
import com.syncnest.userservice.service.GoogleAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String GOOGLE_ISSUER_A = "https://accounts.google.com";
    private static final String GOOGLE_ISSUER_B = "accounts.google.com";

    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuditHistoryService auditHistoryService;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    private volatile JwtDecoder jwtDecoder;

    @Override
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request, DeviceContext context) {
        Objects.requireNonNull(request, "google login request is required");
        Jwt jwt = decodeAndValidate(request.getIdToken());

        String email = normalizeEmail(jwt.getClaimAsString("email"));
        String sub = jwt.getSubject();
        boolean verified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));

        String clientId = request.getClientId() != null && !request.getClientId().isBlank()
                ? request.getClientId().trim()
                : (context != null ? context.getClientId() : null);
        String deviceId = request.getDeviceId() != null && !request.getDeviceId().isBlank()
                ? request.getDeviceId().trim()
                : (context != null ? context.getDeviceId() : null);

        DeviceContext merged = mergeGoogleDeviceContext(context, clientId, deviceId);
        GoogleProfileSnapshot snapshot = GoogleProfileSnapshot.fromJwt(jwt);
        return loginWithVerifiedGoogleClaims(email, sub, verified, merged, snapshot);
    }

    @Override
    @Transactional
    public LoginResponse loginWithVerifiedGoogleClaims(
            String email,
            String sub,
            boolean emailVerified,
            DeviceContext context,
            GoogleProfileSnapshot googleProfile) {
        email = normalizeEmail(email);
        if (email == null || sub == null || sub.isBlank()) {
            auditHistoryService.record(
                    AuditEventType.LOGIN,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    "GOOGLE_EMAIL_NOT_VERIFIED"
            );
            throw new IllegalArgumentException("Google account email is not verified.");
        }
        if (!emailVerified) {
            auditHistoryService.record(
                    AuditEventType.LOGIN,
                    AuditOutcome.FAILURE,
                    null,
                    email,
                    null,
                    "GOOGLE_EMAIL_NOT_VERIFIED"
            );
            throw new IllegalArgumentException("Google account email is not verified.");
        }

        LinkResult result = upsertAndLinkUser(email, sub);
        User user = result.user();

        if (googleProfile != null && googleProfile.hasAny()) {
            applyGoogleProfile(user.getId(), googleProfile);
        }

        DeviceContext enrichedContext = mergeGoogleDeviceContext(context, null, null);

        User userForTokens = userRepository.findById(user.getId()).orElse(user);
        LoginResponse response = authService.issueTokensFor(userForTokens, enrichedContext, true);

        auditHistoryService.record(
                AuditEventType.LOGIN,
                AuditOutcome.SUCCESS,
                user,
                user.getEmail(),
                null,
                result.newlyLinked() ? "GOOGLE_ACCOUNT_LINKED" : "GOOGLE_LOGIN_SUCCESS"
        );

        return response;
    }

    private static DeviceContext mergeGoogleDeviceContext(DeviceContext context, String clientIdOverride, String deviceIdOverride) {
        String clientId = clientIdOverride != null && !clientIdOverride.isBlank()
                ? clientIdOverride
                : (context != null ? context.getClientId() : null);
        String deviceId = deviceIdOverride != null && !deviceIdOverride.isBlank()
                ? deviceIdOverride
                : (context != null ? context.getDeviceId() : null);

        return DeviceContext.builder()
                .clientId(clientId)
                .deviceId(deviceId)
                .ip(context != null ? context.getIp() : null)
                .userAgent(context != null ? context.getUserAgent() : null)
                .os(context != null ? context.getOs() : null)
                .browser(context != null ? context.getBrowser() : null)
                .location(context != null ? context.getLocation() : null)
                .deviceType(context != null ? context.getDeviceType() : null)
                .provider(AuthProvider.GOOGLE)
                .build();
    }

    /**
     * Merges Google-supplied name and avatar into the user's profile (creates profile if missing).
     */
    private void applyGoogleProfile(java.util.UUID userId, GoogleProfileSnapshot snap) {
        if (snap == null || !snap.hasAny()) {
            return;
        }
        User managed = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found after Google link"));
        Profile profile = managed.getProfile();
        if (profile == null) {
            profile = Profile.builder().user(managed).build();
            managed.setProfile(profile);
        }

        String given = snap.givenName();
        String family = snap.familyName();
        if (!StringUtils.hasText(given) && !StringUtils.hasText(family) && StringUtils.hasText(snap.fullName())) {
            String[] parts = snap.fullName().trim().split("\\s+", 2);
            given = parts[0];
            family = parts.length > 1 ? parts[1] : null;
        }
        if (StringUtils.hasText(given)) {
            profile.setFirstName(truncate(given, 100));
        }
        if (StringUtils.hasText(family)) {
            profile.setLastName(truncate(family, 100));
        }
        if (StringUtils.hasText(snap.pictureUrl())) {
            profile.setProfilePictureUrl(truncate(snap.pictureUrl(), 2048));
        }
        userRepository.save(managed);
        log.debug("Updated profile from Google for userId={}", userId);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private LinkResult upsertAndLinkUser(String email, String sub) {
        User bySub = userRepository.findByGoogleSubAndDeletedAtIsNull(sub).orElse(null);
        if (bySub != null) {
            if (!email.equalsIgnoreCase(bySub.getEmail())) {
                throw new IllegalArgumentException("Google account mismatch detected. Contact support.");
            }
            ensureActive(bySub);
            return new LinkResult(bySub, false);
        }

        User deletedUser = userRepository.findDeletedByGoogleSub(sub)
                .or(() -> userRepository.findDeletedByEmail(email))
                .orElse(null);
        if (deletedUser != null) {
            deletedUser.setDeletedAt(null);
            deletedUser.setEnabled(true);
            deletedUser.setLocked(false);
            deletedUser.setVerified(true);
            deletedUser.setGoogleSub(sub);
            deletedUser.setGoogleLinkedAt(LocalDateTime.now());
            return new LinkResult(userRepository.save(deletedUser), true);
        }

        User byEmail = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (byEmail != null) {
            if (!byEmail.isVerified()) {
                throw new IllegalArgumentException("Please verify your email account before linking Google login.");
            }
            if (byEmail.getGoogleSub() != null && !byEmail.getGoogleSub().isBlank() && !sub.equals(byEmail.getGoogleSub())) {
                throw new IllegalArgumentException("This email is already linked to a different Google account.");
            }
            byEmail.setGoogleSub(sub);
            byEmail.setGoogleLinkedAt(LocalDateTime.now());
            return new LinkResult(userRepository.save(byEmail), true);
        }

        String generatedPassword = passwordEncoder.encode("G#" + UUID.randomUUID());

        User created = User.builder()
                .email(email)
                .password(generatedPassword)
                .role(UserRole.ROLE_USER)
                .isLocked(false)
                .isVerified(true)
                .enabled(true)
                .googleSub(sub)
                .googleLinkedAt(LocalDateTime.now())
                .build();

        return new LinkResult(userRepository.save(created), true);
    }

    private void ensureActive(User user) {
        if (user.isDeleted() || !user.isEnabled() || user.isLocked()) {
            throw new IllegalArgumentException("Account is disabled or locked.");
        }
    }

    private Jwt decodeAndValidate(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Google ID token is required.");
        }

        JwtDecoder decoder = getDecoder();
        try {
            return decoder.decode(idToken);
        } catch (JwtException ex) {
            throw new IllegalArgumentException("Invalid Google ID token.");
        }
    }

    private JwtDecoder getDecoder() {
        JwtDecoder local = jwtDecoder;
        if (local != null) return local;

        synchronized (this) {
            if (jwtDecoder == null) {
                if (googleClientId == null || googleClientId.isBlank()) {
                    throw new IllegalStateException("Google client-id is not configured.");
                }
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
                OAuth2TokenValidator<Jwt> issuerValidator = token -> {
                    String iss = token.getIssuer() != null ? token.getIssuer().toString() : "";
                    boolean valid = GOOGLE_ISSUER_A.equals(iss) || GOOGLE_ISSUER_B.equals(iss);
                    return valid
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid Google issuer", null));
                };

                OAuth2TokenValidator<Jwt> audienceValidator = token -> {
                    boolean ok = token.getAudience() != null && token.getAudience().contains(googleClientId);
                    return ok
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token audience mismatch", null));
                };

                OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithValidators(
                        new JwtTimestampValidator(),
                        issuerValidator,
                        audienceValidator
                );

                decoder.setJwtValidator(validator);
                jwtDecoder = decoder;
            }
            return jwtDecoder;
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record LinkResult(User user, boolean newlyLinked) {}
}



