package com.syncnest.userservice.utils;

import lombok.NonNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM = "SYSTEM";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Unauthenticated or anonymous → SYSTEM
        if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
            return Optional.of(SYSTEM);
        }

        String identifier = resolveIdentifier(auth); // email when available
        String prefix = hasAdminRole(auth.getAuthorities()) ? "ADMIN" : "USER";
        return Optional.of(prefix + ":" + identifier);
    }

    private boolean isAnonymous(Authentication auth) {
        Object principal = auth.getPrincipal();
        return principal == null || "anonymousUser".equals(principal);
    }

    private boolean hasAdminRole(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) return false;
        return authorities.stream().anyMatch(ga -> ROLE_ADMIN.equals(ga.getAuthority()));
    }

    /**
     * Resolve a stable human identifier for auditing.
     * Priority: Spring Security (UserDetails -> email) > JWT (email/preferred_username/sub) > OAuth2 (email/preferred_username/name/sub) > auth.getName()
     */
    private String resolveIdentifier(Authentication auth) {
        Object principal = auth.getPrincipal();

        // 1) Local auth (UserDetails). In our app, getUsername() returns email.
        if (principal instanceof UserDetails ud) {
            String email = ud.getUsername(); // mapped to email in User entity
            if (notBlank(email)) return email;
        }

        // 2) JWT (resource server)
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            var jwt = jwtAuth.getToken();
            String email = jwt.getClaimAsString("email");
            if (notBlank(email)) return email;

            String preferred = jwt.getClaimAsString("preferred_username");
            if (notBlank(preferred)) return preferred;

            String sub = jwt.getClaimAsString("sub");
            if (notBlank(sub)) return sub;

            return safe(auth.getName());
        }

        // 3) OAuth2 (e.g., Google / Apple)
        if (auth instanceof OAuth2AuthenticationToken oauth) {
            Map<String, Object> attrs = oauth.getPrincipal().getAttributes();

            String email = str(attrs.get("email"));
            if (notBlank(email)) return email;

            String preferred = str(attrs.get("preferred_username"));
            if (notBlank(preferred)) return preferred;

            String name = str(attrs.get("name"));
            if (notBlank(name)) return name;

            String sub = str(attrs.get("sub"));
            if (notBlank(sub)) return sub;

            return safe(auth.getName());
        }

        // 4) Fallback
        return safe(auth.getName());
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "unknown" : s;
    }
}
