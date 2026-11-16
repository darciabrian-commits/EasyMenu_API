package org.esfe.easymenu.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long EXPIRATION_TIME = 86400000;
    private static final int MINIMUM_KEY_BYTES = 32;

    private final Key signingKey;

    public JwtUtil(
            @Value("${jwt.secret}") String secretKey
    ) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must not be empty");
        }

        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64", exception);
        }
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 256 bits"
            );
        }

        try {
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (WeakKeyException exception) {
            throw new IllegalStateException("JWT_SECRET is too weak for HS256", exception);
        }
    }

    public String generarToken(UserDetails userDetails) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(
                                System.currentTimeMillis()
                                        + EXPIRATION_TIME
                        )
                )
                .signWith(
                        signingKey,
                        SignatureAlgorithm.HS256
                )
                .compact();
    }

    public String obtenerUsuarioDelToken(String token) {
        return obtenerClaims(token).getSubject();
    }

    public boolean validarToken(
            String token,
            UserDetails userDetails
    ) {
        if (userDetails == null) {
            return false;
        }
        Claims claims = obtenerClaims(token);
        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    private Claims obtenerClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("JWT must not be empty");
        }

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("JWT must contain an expiration claim");
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new MalformedJwtException("JWT must contain a subject");
        }
        return claims;
    }
}