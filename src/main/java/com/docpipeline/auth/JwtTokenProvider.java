package com.docpipeline.auth;

import com.docpipeline.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class JwtTokenProvider {

    private final AppProperties appProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.signingKey = buildSigningKey(appProperties.getJwt().getSecret());
    }

    /**
     * Builds the HMAC-SHA256 signing key from the configured secret.
     *
     * Accepts two formats:
     *  1. Base64-encoded string (preferred for production) — produced by: openssl rand -base64 32
     *  2. Plain UTF-8 string (acceptable for local dev) — must be at least 32 characters
     *
     * Throws a clear IllegalStateException on startup if the key is too weak,
     * instead of an opaque constructor exception in CloudWatch logs.
     */
    private SecretKey buildSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET is not configured. Set the JWT_SECRET environment variable " +
                "to a Base64-encoded 256-bit key (generate with: openssl rand -base64 32)");
        }

        // Try Base64 decoding first (production path)
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            if (keyBytes.length >= 32) { // 32 bytes = 256 bits minimum for HMAC-SHA256
                log.debug("JWT signing key loaded from Base64-encoded secret ({} bytes)", keyBytes.length);
                return Keys.hmacShaKeyFor(keyBytes);
            }
            log.warn("Base64-decoded JWT secret is only {} bytes — minimum is 32. " +
                     "Falling back to raw UTF-8. Use 'openssl rand -base64 32' for production.", keyBytes.length);
        } catch (IllegalArgumentException e) {
            log.debug("JWT secret is not Base64-encoded, treating as plain UTF-8 string.");
        }

        // Fallback: use raw UTF-8 bytes (local dev with plain-text secret)
        byte[] rawBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (rawBytes.length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET is too short (" + rawBytes.length + " bytes). " +
                "Minimum is 32 bytes (256 bits). " +
                "Generate a valid key with: openssl rand -base64 32");
        }
        log.warn("Using plain UTF-8 JWT secret. For production, use a Base64-encoded key: openssl rand -base64 32");
        return Keys.hmacShaKeyFor(rawBytes);
    }


    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + appProperties.getJwt().getExpiration()))
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
