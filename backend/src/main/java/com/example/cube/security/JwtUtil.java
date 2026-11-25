package com.example.cube.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;

    private final Key signingKey;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret:}") String secret,
                   @Value("${jwt.expiration:3600000}") long expirationMs) {
        if (secret == null || secret.trim().isEmpty() || secret.contains("CHANGE_ME")) {
            // No secret configured: use a random key so the app still starts securely.
            // Tokens become invalid on restart and are not shared between cluster nodes,
            // so set CUBE_JWT_SECRET for any real deployment.
            byte[] random = new byte[64];
            new SecureRandom().nextBytes(random);
            this.signingKey = Keys.hmacShaKeyFor(random);
            LoggerFactory.getLogger(JwtUtil.class).warn(
                    "CUBE_JWT_SECRET is not set - using a random signing key. Users must log in again after every "
                            + "restart. Set CUBE_JWT_SECRET (32+ characters) for production.");
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("jwt.secret must be at least " + MIN_SECRET_BYTES + " characters long.");
            }
            this.signingKey = Keys.hmacShaKeyFor(bytes);
        }
        this.expirationMs = expirationMs;
    }

    public IssuedToken generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .setId(tokenId)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
        return new IssuedToken(token, tokenId, expiry);
    }

    /** Returns the claims of a valid, unexpired token; empty if invalid. */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    public static final class IssuedToken {
        private final String token;
        private final String tokenId;
        private final Date expiresAt;

        public IssuedToken(String token, String tokenId, Date expiresAt) {
            this.token = token;
            this.tokenId = tokenId;
            this.expiresAt = new Date(expiresAt.getTime());
        }

        public String getToken() {
            return token;
        }

        public String getTokenId() {
            return tokenId;
        }

        public Date getExpiresAt() {
            return new Date(expiresAt.getTime());
        }
    }
}
