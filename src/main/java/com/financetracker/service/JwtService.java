package com.financetracker.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // Email is embedded as a custom claim so the filter can populate it in the
    // SecurityContext without a DB round-trip on every request.
    public String generateToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(userId.toString())          // "sub" claim — the user's UUID
                .claim("email", email)               // custom claim — carried inside the payload
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey())               // HMAC-SHA256 signature
                .compact();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(claims(token).getSubject());
    }

    public String extractEmail(String token) {
        return claims(token).get("email", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private io.jsonwebtoken.Claims claims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
