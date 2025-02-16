package com.jithin.ai_content_platform.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Value("${app.jwt-expiration-milliseconds}")
    private int jwtExpirationInMs;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        logger.debug("Initializing JWT secret key");
        try {
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            // Ensure the key is at least 512 bits (64 bytes) for HS512
            if (keyBytes.length < 64) {
                byte[] paddedKey = new byte[64];
                System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
                // Pad the remaining bytes with zeros
                for (int i = keyBytes.length; i < 64; i++) {
                    paddedKey[i] = 0;
                }
                keyBytes = paddedKey;
            }
            secretKey = Keys.hmacShaKeyFor(keyBytes);
            logger.debug("JWT secret key initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize JWT secret key", e);
            throw new IllegalStateException("Failed to initialize JWT secret key", e);
        }
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        logger.debug("Generating token for user: {}", username);
        
        try {
            String token = Jwts.builder()
                    .setSubject(username)
                    .setIssuedAt(now)
                    .setExpiration(expiryDate)
                    .signWith(secretKey, SignatureAlgorithm.HS512)
                    .compact();

            logger.debug("Token generated successfully");
            return token;
        } catch (Exception e) {
            logger.error("Error generating token", e);
            throw new RuntimeException("Error generating token", e);
        }
    }

    public String getUsernameFromJWT(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String username = claims.getSubject();
            logger.debug("Username extracted from token: {}", username);
            return username;
        } catch (Exception e) {
            logger.error("Error extracting username from token", e);
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            if (token == null) {
                logger.error("Token is null");
                return false;
            }

            if (secretKey == null) {
                logger.error("Secret key is null - initialization failed");
                return false;
            }

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Check if token is expired
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                logger.error("Token is expired. Expiration: {}", expiration);
                return false;
            }

            logger.debug("Token validated successfully for user: {}", claims.getSubject());
            return true;
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("JWT validation error: {}", e.getMessage());
        }
        return false;
    }
}