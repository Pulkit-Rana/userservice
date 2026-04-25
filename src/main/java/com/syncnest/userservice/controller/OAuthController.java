package com.syncnest.userservice.controller;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.dto.GoogleLoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.service.GoogleAuthService;
import com.syncnest.userservice.utils.RequestMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Google sign-in for API clients (SPA/mobile) that send a Google ID token.
 * <p>For the classic browser redirect flow, use {@code GET /oauth2/authorization/google}
 * (see {@code application.properties} and Google Cloud redirect URI configuration).
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

	@Value("${app.cookie.secure:false}")
	private boolean refreshCookieSecure;

	@Value("${refresh-token.expiration.milliseconds:2592000000}")
	private long refreshTokenExpirationMs;

	private final GoogleAuthService googleAuthService;
	private final RequestMetadataExtractor metadataExtractor;

	@PostMapping("/google/login")
	public ResponseEntity<LoginResponse> googleLogin(
			@Valid @RequestBody GoogleLoginRequest request,
			HttpServletRequest httpRequest) {

		String ip = metadataExtractor.extractIp(httpRequest);
		String ua = metadataExtractor.extractUserAgent(httpRequest);

		DeviceContext context = DeviceContext.builder()
				.clientId(request.getClientId())
				.deviceId(request.getDeviceId())
				.ip(ip)
				.userAgent(ua)
				.os(metadataExtractor.parseOs(ua))
				.browser(metadataExtractor.parseBrowser(ua))
				.deviceType(metadataExtractor.parseDeviceType(ua))
				.provider(AuthProvider.GOOGLE)
				.build();

		LoginResponse response = googleAuthService.loginWithGoogle(request, context);

		ResponseCookie rtCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
				.httpOnly(true)
				.secure(refreshCookieSecure)
				.sameSite("Strict")
				.path("/")
				.maxAge(Duration.ofMillis(refreshTokenExpirationMs))
				.build();

		response.setRefreshToken(null);
		log.info("Google login successful for email={}", response.getUser().getEmail());

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, rtCookie.toString())
				.body(response);
	}

}
