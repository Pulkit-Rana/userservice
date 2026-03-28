package com.syncnest.userservice.controller;

import com.syncnest.userservice.dto.AccountDeletionResponse;
import com.syncnest.userservice.dto.AccountRestorationResponse;
import com.syncnest.userservice.dto.RestoreAccountRequest;
import com.syncnest.userservice.service.UserAccountService;
import com.syncnest.userservice.utils.RequestMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;
    private final RequestMetadataExtractor metadataExtractor;

    @DeleteMapping("/me")
    public ResponseEntity<AccountDeletionResponse> softDeleteCurrentUser(
            Authentication authentication,
            HttpServletRequest request) {

        String email = authentication != null ? authentication.getName() : null;
        String ip = metadataExtractor.extractIp(request);
        String userAgent = metadataExtractor.extractUserAgent(request);

        AccountDeletionResponse response = userAccountService.softDeleteByEmail(email, ip, userAgent);
        log.info("Soft delete completed for current user={}", email);
        return ResponseEntity.ok(response);
    }
}

