package com.syncnest.userservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * Logs the exact Google OAuth2 redirect URI at startup so it can be copied into
 * Google Cloud Console → Credentials → OAuth client → Authorized redirect URIs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2GoogleRedirectUriLogger {

    private final ClientRegistrationRepository clientRegistrationRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void logGoogleRedirectUri() {
        try {
            ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
            if (google == null) {
                log.warn("No OAuth2 client registration id 'google' — browser Google login will not work.");
                return;
            }
            String uri = google.getRedirectUri();
            log.info(
                    "Google OAuth2: add this EXACT string to Google Cloud Console → Authorized redirect URIs: {}",
                    uri);
            log.info(
                    "Google OAuth2: open (API base) …/oauth2/authorization/google — NOT the SPA port for redirect_uri.");
        } catch (Exception ex) {
            log.warn("Could not resolve Google OAuth2 redirect URI for logging: {}", ex.getMessage());
        }
    }
}
