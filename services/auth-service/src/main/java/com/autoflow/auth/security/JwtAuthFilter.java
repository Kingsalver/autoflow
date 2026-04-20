package com.autoflow.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads the access JWT from the "access_token" HttpOnly cookie.
 * On success, sets an Authentication in the SecurityContext so downstream
 * controllers can call SecurityContextHolder.getContext().getAuthentication().
 *
 * Invalid / missing tokens simply result in an unauthenticated request —
 * Spring Security will enforce 401 on protected routes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ACCESS_COOKIE_NAME  = "access_token";
    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        CookieUtil.extractCookie(request, ACCESS_COOKIE_NAME)
                .flatMap(this::tryValidate)
                .ifPresent(claims -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),    // principal = userId string
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    auth.setDetails(claims);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });

        chain.doFilter(request, response);
    }

    private Optional<Claims> tryValidate(String token) {
        try {
            return Optional.of(jwtUtil.validateAccessToken(token));
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}