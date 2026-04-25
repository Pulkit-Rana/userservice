package com.syncnest.userservice.service;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.dto.GoogleLoginRequest;
import com.syncnest.userservice.dto.GoogleProfileSnapshot;
import com.syncnest.userservice.dto.LoginResponse;

public interface GoogleAuthService {

    LoginResponse loginWithGoogle(GoogleLoginRequest request, DeviceContext context);

    /**
     * Completes Google login using already-verified OpenID claims (ID token path or OAuth2 user-info path).
     * Applies the same account linking and token issuance rules as {@link #loginWithGoogle}.
     */
    default LoginResponse loginWithVerifiedGoogleClaims(String email, String sub, boolean emailVerified, DeviceContext context) {
        return loginWithVerifiedGoogleClaims(email, sub, emailVerified, context, null);
    }

    /**
     * Same as {@link #loginWithVerifiedGoogleClaims(String, String, boolean, DeviceContext)} but merges
     * optional Google profile fields (name, picture) into {@link com.syncnest.userservice.entity.Profile}.
     */
    LoginResponse loginWithVerifiedGoogleClaims(
            String email,
            String sub,
            boolean emailVerified,
            DeviceContext context,
            GoogleProfileSnapshot googleProfile);
}

