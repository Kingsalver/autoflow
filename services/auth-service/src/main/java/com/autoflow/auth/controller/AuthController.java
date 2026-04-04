package com.autoflow.auth.controller;

import com.autoflow.auth.dto.UserResponse;
import com.autoflow.auth.entity.User;
import com.autoflow.auth.repository.UserRepository;
import com.autoflow.auth.security.CookieUtil;
import com.autoflow.auth.security.JwtAuthFilter;
import com.autoflow.auth.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final UserRepository userRepository;

    /**
     * GET /auth/me
     * Returns the currently authenticated user's profile.
     * Requires a valid access token cookie.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * POST /auth/refresh
     * Reads the refresh token from its HttpOnly cookie, validates it,
     * and issues a new access token + rotated refresh token.
     *
     * Refresh token rotation: each use issues a new refresh token.
     * The old one isn't explicitly blocklisted here — since it's stored
     * in a cookie scoped to /auth/refresh, it won't be sent elsewhere anyway.
     * Add DB-level refresh token tracking in Phase 2 if stricter revocation is needed.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, JwtAuthFilter.REFRESH_COOKIE_NAME)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token"));

        try {
            Claims claims = jwtUtil.validateRefreshToken(refreshToken);
            String userId = claims.getSubject();

            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

            // Issue fresh token pair
            cookieUtil.addAccessTokenCookie(response, jwtUtil.issueAccessToken(userId, user.getEmail()));
            cookieUtil.addRefreshTokenCookie(response, jwtUtil.issueRefreshToken(userId));

            return ResponseEntity.noContent().build();

        } catch (JwtException e) {
            cookieUtil.clearAuthCookies(response);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
    }

    /**
     * POST /auth/logout
     * Blocklists the current access token in Redis, clears both cookies.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        extractCookie(request, JwtAuthFilter.ACCESS_COOKIE_NAME)
                .ifPresent(jwtUtil::blocklist);

        cookieUtil.clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Optional<String> extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}