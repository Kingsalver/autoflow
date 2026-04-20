package com.autoflow.auth.security;

import com.autoflow.auth.config.AppProperties;
import com.autoflow.auth.entity.OAuthToken;
import com.autoflow.auth.entity.User;
import com.autoflow.auth.repository.OAuthTokenRepository;
import com.autoflow.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Invoked by Spring Security after a successful OAuth2 login.
 *
 * Responsibilities:
 *  1. Extract the user's email + display name from the OAuth2 user info response
 *  2. Upsert a row in `users`
 *  3. Upsert the provider access/refresh token in `oauth_tokens`
 *  4. Issue our own short-lived access JWT + long-lived refresh JWT
 *  5. Write both as HttpOnly cookies
 *  6. Redirect the browser to the frontend
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuthTokenRepository oauthTokenRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final AppProperties props;

    private final RestClient restClient = RestClient.create();

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId(); // "github"

        // ── 1. Load the authorized client first (needed for private email fallback) ──
        OAuth2AuthorizedClient client = authorizedClientService
                .loadAuthorizedClient(registrationId, oauthToken.getName());

        // ── 2. Extract user info from GitHub user-info response ───────────────
        String email       = resolveEmail(oauthUser, registrationId, client);
        String displayName = resolveDisplayName(oauthUser);

        if (email == null) {
            log.error("No email returned from OAuth2 provider '{}' — aborting login", registrationId);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Email not available from provider");
            return;
        }

        // ── 3. Upsert user ────────────────────────────────────────────────────
        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setDisplayName(displayName);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(
                        User.builder().email(email).displayName(displayName).build()
                ));

        // ── 4. Upsert provider token ─────────────────────────────────────────
        if (client != null && client.getAccessToken() != null) {
            String accessToken  = client.getAccessToken().getTokenValue();
            String refreshToken = client.getRefreshToken() != null
                    ? client.getRefreshToken().getTokenValue() : null;
            OffsetDateTime expiresAt = client.getAccessToken().getExpiresAt() != null
                    ? OffsetDateTime.ofInstant(client.getAccessToken().getExpiresAt(),
                    java.time.ZoneOffset.UTC)
                    : null;

            OAuthToken tokenRecord = oauthTokenRepository
                    .findByUserAndProvider(user, registrationId)
                    .orElseGet(() -> OAuthToken.builder().user(user).provider(registrationId).build());

            tokenRecord.setAccessToken(accessToken);
            tokenRecord.setRefreshToken(refreshToken);
            tokenRecord.setExpiresAt(expiresAt);
            oauthTokenRepository.save(tokenRecord);
        }

        // ── 5 + 6. Issue JWTs and write cookies ───────────────────────────────
        String userId = user.getId().toString();
        cookieUtil.addAccessTokenCookie(response, jwtUtil.issueAccessToken(userId, email));
        cookieUtil.addRefreshTokenCookie(response, jwtUtil.issueRefreshToken(userId));

        // ── 7. Redirect to frontend ───────────────────────────────────────────
        log.info("Successful login for user {} via {}", email, registrationId);
        getRedirectStrategy().sendRedirect(request, response, props.getFrontend().getRedirectUrl());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveEmail(OAuth2User user, String registrationId, OAuth2AuthorizedClient client) {
        Object email = user.getAttribute("email");
        if (email != null) return email.toString();

        // GitHub users with private emails: the /user endpoint returns null for email.
        // Fall back to /user/emails to find the primary verified address.
        if ("github".equals(registrationId) && client != null
                && client.getAccessToken() != null) {
            String accessToken = client.getAccessToken().getTokenValue();
            try {
                List<Map<String, Object>> emails = restClient.get()
                        .uri("https://api.github.com/user/emails")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/vnd.github+json")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                if (emails != null) {
                    return emails.stream()
                            .filter(e -> Boolean.TRUE.equals(e.get("verified"))
                                      && Boolean.TRUE.equals(e.get("primary")))
                            .map(e -> (String) e.get("email"))
                            .findFirst()
                            .or(() -> emails.stream()
                                    .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                                    .map(e -> (String) e.get("email"))
                                    .findFirst())
                            .orElse(null);
                }
            } catch (Exception ex) {
                log.warn("Failed to fetch GitHub private emails: {}", ex.getMessage());
            }
        }
        return null;
    }

    private String resolveDisplayName(OAuth2User user) {
        // GitHub: "name" is the display name, "login" is the username fallback
        Object name = user.getAttribute("name");
        if (name != null) return name.toString();
        Object login = user.getAttribute("login");
        return login != null ? login.toString() : null;
    }
}