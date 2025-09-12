package com.syncnest.userservice.SecurityConfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Component
public class JwtTokenProviderConfig {

    @Value("${token.key.secret}")
    private String secret; // Base64-encoded HMAC secret

    @Value("${token.key.jwtExpiration}")
    private int jwtExpiration; // milliseconds

    // OPTIONAL: enforce issuer/audience only if configured (kept blank = no enforcement)
    @Value("${token.key.issuer:}")
    private String issuerOpt;

    @Value("${token.key.audience:}")
    private String audienceOpt;

    /** Cached signing key & parser for performance */
    private SecretKey signingKey;
    private io.jsonwebtoken.JwtParser jwtParser;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must be provided (base64).");
        }
        final byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT secret must be valid Base64.", e);
        }
        // HS256 requires >= 256-bit (32 bytes) key; enforce minimum.
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret too short for HS256. Provide >= 256-bit Base64 key.");
        }

        signingKey = Keys.hmacShaKeyFor(keyBytes);

        // Build a reusable, thread-safe parser; enforce iss/aud only when configured
        var parserBuilder = Jwts.parser()
                .verifyWith(signingKey)
                .clockSkewSeconds(30); // tolerate small clock drift

        if (issuerOpt != null && !issuerOpt.isBlank()) {
            parserBuilder = parserBuilder.requireIssuer(issuerOpt);
        }
        if (audienceOpt != null && !audienceOpt.isBlank()) {
            parserBuilder = parserBuilder.requireAudience(audienceOpt);
        }
        jwtParser = parserBuilder.build();
    }

    /** Extract the email (subject) from the JWT token. */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extract the expiration date from the JWT token. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /** Extract a specific claim from the token using a claims resolver function. */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /** Extract all claims from the JWT token. (Verifies signature and required claims) */
    private Claims extractAllClaims(String token) {
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT Token: {}", e.getMessage());
            throw new IllegalArgumentException("Failed to parse JWT token.", e);
        }
    }

    /** Check if the token is expired. */
    public boolean isTokenExpired(String token) {
        Date exp = extractExpiration(token);
        return exp != null && exp.before(new Date());
    }

    /**
     * Validate the token against the user details and ensure it is not expired.
     * Uses a single parse (already verifies signature + optional iss/aud).
     * NOTE: userDetails.getUsername() should return the email in your User entity.
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            Claims claims = extractAllClaims(token);
            String subjectEmail = claims.getSubject();
            Date exp = claims.getExpiration();
            if (subjectEmail == null || userDetails == null) return false;
            if (exp != null && exp.before(new Date())) return false;
            return subjectEmail.equals(userDetails.getUsername());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Generate a new JWT token for the given email (as subject). */
    public String generateToken(String email) {
        return createToken(new HashMap<>(), email);
    }

    /** Create the JWT token with custom claims, subject=email, and expiration. */
    private String createToken(Map<String, Object> claims, String email) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date notBefore = new Date(now); // can offset if needed
        Date expiration = new Date(now + Math.max(0, jwtExpiration)); // guard negative

        // Include optional standard claims if configured
        if (issuerOpt != null && !issuerOpt.isBlank()) {
            claims.putIfAbsent("iss", issuerOpt);
        }
        if (audienceOpt != null && !audienceOpt.isBlank()) {
            claims.putIfAbsent("aud", audienceOpt);
        }
        // Include a JWT ID for tracing/blacklisting (safe to add)
        claims.putIfAbsent("jti", UUID.randomUUID().toString().replace("-", ""));

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(issuedAt)
                .notBefore(notBefore)
                .expiration(expiration)
                // Explicit algorithm selection for clarity & safety (jjwt 0.12)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Resolve refresh token cookie value (HttpOnly/Secure flags should be set where the cookie is created). */
    public Optional<String> resolveRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    public int getTokenValiditySeconds() {
        return jwtExpiration / 1000;
    }
}
