package com.syncnest.userservice.dto;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * Optional profile fields from Google (ID token or OAuth2 user-info) applied to {@link com.syncnest.userservice.entity.Profile}.
 */
public record GoogleProfileSnapshot(String givenName, String familyName, String fullName, String pictureUrl) {

    public static GoogleProfileSnapshot fromJwt(Jwt jwt) {
        return new GoogleProfileSnapshot(
                str(jwt.getClaimAsString("given_name")),
                str(jwt.getClaimAsString("family_name")),
                str(jwt.getClaimAsString("name")),
                str(jwt.getClaimAsString("picture"))
        );
    }

    public static GoogleProfileSnapshot fromOAuth2User(OAuth2User user) {
        return new GoogleProfileSnapshot(
                str(user.getAttribute("given_name")),
                str(user.getAttribute("family_name")),
                str(user.getAttribute("name")),
                str(user.getAttribute("picture"))
        );
    }

    private static String str(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    public boolean hasAny() {
        return StringUtils.hasText(givenName) || StringUtils.hasText(familyName)
                || StringUtils.hasText(fullName) || StringUtils.hasText(pictureUrl);
    }
}
