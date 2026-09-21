package com.plexus.personal.auth.infrastructure.security;

import com.plexus.personal.auth.api.LoginResponse;
import com.plexus.personal.user.domain.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtTokenService(
            @Value("${atlasmind.jwt.secret}") String secret,
            @Value("${atlasmind.jwt.expiration-seconds}") long expirationSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public LoginResponse issue(User user) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + expirationSeconds * 1000);
        String token = Jwts.builder()
                .subject(user.id().toString())
                .claim("username", user.username())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();

        return new LoginResponse("Bearer", token, expirationSeconds);
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }
}
