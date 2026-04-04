package com.autoflow.auth.security;

import com.autoflow.auth.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles JWT lifecycle:
 *  - Access token:  15 min TTL, contains userId + email
 *  - Refresh token: 7 day TTL, contains userId only
 *
 * Revocation: on logout the access token's jti is stored in Redis
 * with a TTL equal to the token's remaining lifetime. Refresh tokens
 * are deleted from the DB, so no Redis entry is needed for them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String TOKEN_TYPE_CLAIM  = "type";
    private static final String TOKEN_TYPE_ACCESS  = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String BLOCKLIST_PREFIX   = "jwt:blocklist:";

    private final AppProperties props;
    private final StringRedisTemplate redis;

    // ── Key ──────────────────────────────────────────────────────────────────

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(props.getJwt().getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Issue ────────────────────────────────────────────────────────────────

    public String issueAccessToken(String userId, String email) {
        long expiryMs = (long) props.getJwt().getAccessTokenExpiryMinutes() * 60 * 1000;
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("email", email)
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(signingKey())
                .compact();
    }

    public String issueRefreshToken(String userId) {
        long expiryMs = (long) props.getJwt().getRefreshTokenExpiryDays() * 24 * 60 * 60 * 1000;
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(signingKey())
                .compact();
    }

    // ── Parse ────────────────────────────────────────────────────────────────

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates the token and confirms it is an access token.
     * Returns parsed claims on success, throws on any failure.
     */
    public Claims validateAccessToken(String token) {
        Claims claims = parseToken(token);

        if (!TOKEN_TYPE_ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Token is not an access token");
        }
        if (isBlocklisted(claims.getId())) {
            throw new JwtException("Token has been revoked");
        }
        return claims;
    }

    public Claims validateRefreshToken(String token) {
        Claims claims = parseToken(token);
        if (!TOKEN_TYPE_REFRESH.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Token is not a refresh token");
        }
        return claims;
    }

    // ── Blocklist (Redis) ────────────────────────────────────────────────────

    /**
     * Adds the access token's jti to the Redis blocklist.
     * TTL is set to the token's remaining lifetime so Redis auto-cleans it.
     */
    public void blocklist(String token) {
        try {
            Claims claims = parseToken(token);
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs > 0) {
                redis.opsForValue().set(
                        BLOCKLIST_PREFIX + claims.getId(),
                        "1",
                        remainingMs,
                        TimeUnit.MILLISECONDS
                );
            }
        } catch (JwtException e) {
            // Already expired — no need to blocklist
            log.debug("Attempted to blocklist already-expired token: {}", e.getMessage());
        }
    }

    private boolean isBlocklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLOCKLIST_PREFIX + jti));
    }
}