package com.autoflow.auth.security;

import com.autoflow.auth.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Creates HttpOnly cookies with consistent security attributes.
 *
 * We use ResponseCookie (Spring 5.3+) instead of javax.servlet.http.Cookie
 * because it supports the SameSite attribute, which Cookie does not expose
 * directly through its API.
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final AppProperties props;

    /**
     * Write the access token cookie.
     * Path = "/" so it's sent on every request.
     */
    public void addAccessTokenCookie(HttpServletResponse response, String token) {
        long maxAge = (long) props.getJwt().getAccessTokenExpiryMinutes() * 60;
        ResponseCookie cookie = buildCookie(JwtAuthFilter.ACCESS_COOKIE_NAME, token, "/", maxAge);
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Write the refresh token cookie.
     * Path = "/auth/refresh" so the browser only sends it to that endpoint —
     * this limits exposure compared to sending it on every request.
     */
    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        long maxAge = (long) props.getJwt().getRefreshTokenExpiryDays() * 24 * 60 * 60;
        ResponseCookie cookie = buildCookie(JwtAuthFilter.REFRESH_COOKIE_NAME, token, "/auth/refresh", maxAge);
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** Clear both cookies (logout). */
    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie(JwtAuthFilter.ACCESS_COOKIE_NAME, "", "/", 0).toString());
        response.addHeader("Set-Cookie", buildCookie(JwtAuthFilter.REFRESH_COOKIE_NAME, "", "/auth/refresh", 0).toString());
    }

    private ResponseCookie buildCookie(String name, String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.getCookie().isSecure())
                .sameSite(props.getCookie().getSameSite())
                .path(path)
                .maxAge(maxAgeSeconds)
                .build();
    }
}