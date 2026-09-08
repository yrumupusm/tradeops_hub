package io.tradeops.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.tradeops.user.AppUser;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final String issuer;
    private final long expirationSeconds;

    public JwtService(
            @Value("${tradeops.security.jwt-secret}") String encodedSecret,
            @Value("${tradeops.security.jwt-issuer}") String issuer,
            @Value("${tradeops.security.jwt-expiration-minutes}") long expirationMinutes
    ) {
        byte[] secret = Decoders.BASE64.decode(encodedSecret);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 256 bits.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
        this.issuer = issuer;
        this.expirationSeconds = expirationMinutes * 60;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public long getExpirationSeconds() { return expirationSeconds; }
}
