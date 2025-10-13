package com.matjom.matjom.common.security.jwt;

import com.matjom.matjom.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret-base64:cCmyrSYHdZ/ApKxvA5yef5C30xMNhv1B2jGqEo4cY7Q=}")
    private String secretKey;

    private static final long ACCESS_TOKEN_VALIDITY = 15 * 60 * 1000L;
    private static final long REFRESH_TOKEN_VALIDITY = 14 * 24 * 60 * 60 * 1000L;
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private SecretKey key;

    @PostConstruct
    protected void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(User user) {
        return createToken(user, ACCESS_TOKEN_VALIDITY, TOKEN_TYPE_ACCESS);
    }

    public String createRefreshToken(User user) {
        return createToken(user, REFRESH_TOKEN_VALIDITY, TOKEN_TYPE_REFRESH);
    }

    private String createToken(User user, long validityInMillis, String tokenType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMillis);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getRefreshTokenValidity() {
        return REFRESH_TOKEN_VALIDITY;
    }

    public Claims getClaimsEvenIfExpired(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public Duration getRemainingValidity(String token) {
        Date expiration = getClaimsEvenIfExpired(token).getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    public UUID getUserId(String token) {
        return UUID.fromString(getClaimsEvenIfExpired(token).getSubject());
    }

    public String getEmailFromToken(String token) {
        return getClaimsEvenIfExpired(token).get("email", String.class);
    }

    public String getTokenType(String token) {
        return getClaimsEvenIfExpired(token).get(CLAIM_TOKEN_TYPE, String.class);
    }
}