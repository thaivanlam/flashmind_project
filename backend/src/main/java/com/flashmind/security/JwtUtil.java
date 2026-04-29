package com.flashmind.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-expiration}")
    private long accessExpiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    public String generateAccessToken(Long userId, String email) {
        return buildToken(userId, email, accessExpiration, "access");
    }

    public String generateRefreshToken(Long userId, String email) {
        return buildToken(userId, email, refreshExpiration, "refresh");
    }

    private String buildToken(Long userId, String email, long expiration, String type) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
            .subject(email)
            .claim("userId", userId)
            .claim("type", type)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    public String extractEmail(String token) {
        return parseToken(token).getSubject();
    }

    public String extractType(String token) {
        return parseToken(token).get("type", String.class);
    }
}
