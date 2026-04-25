package com.syncnest.userservice.controller;

import com.syncnest.userservice.dto.AccountDeletionResponse;
import com.syncnest.userservice.dto.AccountRestorationResponse;
import com.syncnest.userservice.dto.ChangePasswordRequest;
import com.syncnest.userservice.dto.MeProfileResponse;
import com.syncnest.userservice.dto.RestoreAccountRequest;
import com.syncnest.userservice.dto.UpdateProfileRequest;
import com.syncnest.userservice.logging.LogSanitizer;
import com.syncnest.userservice.service.UserAccountService;
import com.syncnest.userservice.utils.RequestMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;
    private final RequestMetadataExtractor metadataExtractor;

    /**
     * Current user profile (JWT). Includes Google-linked avatar when present.
     */
    @GetMapping("/me")
    public ResponseEntity<MeProfileResponse> getCurrentProfile(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        MeProfileResponse body = userAccountService.getCurrentProfile(email);
        return ResponseEntity.ok(body);
    }

    /**
     * Updates editable profile fields (names). Picture URL is applied from Google sign-in on the server only.
     */
    @PutMapping("/me/profile")
    public ResponseEntity<MeProfileResponse> updateCurrentProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        String email = authentication != null ? authentication.getName() : null;
        MeProfileResponse body = userAccountService.updateCurrentProfile(email, request);
        return ResponseEntity.ok(body);
    }

    /**
     * Changes password for verified accounts. Requires current password. Revokes all refresh sessions.
     */
    @PostMapping("/me/password")
    public ResponseEntity<java.util.Map<String, Object>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        String email = authentication != null ? authentication.getName() : null;
        userAccountService.changePassword(email, request);
        return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Password updated. Please sign in again on this device; other sessions have been signed out."
        ));
    }

    @DeleteMapping("/me")
    public ResponseEntity<AccountDeletionResponse> softDeleteCurrentUser(
            Authentication authentication,
            HttpServletRequest request) {

        String email = authentication != null ? authentication.getName() : null;
        String ip = metadataExtractor.extractIp(request);
        String userAgent = metadataExtractor.extractUserAgent(request);

        AccountDeletionResponse response = userAccountService.softDeleteByEmail(email, ip, userAgent);
        log.info("Soft delete completed for current user={}", LogSanitizer.maskEmail(email));
        return ResponseEntity.ok(response);
    }
}



