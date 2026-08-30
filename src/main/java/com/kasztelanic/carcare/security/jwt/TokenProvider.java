package com.kasztelanic.carcare.security.jwt;

import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.management.SecurityMetersService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenProvider implements InitializingBean {

    private static final String AUTHORITIES_KEY = "auth";

    private final SecurityMetersService securityMetersService;
    private final ApplicationProperties applicationProperties;

    private SecretKey key;
    private long tokenValidityInMilliseconds;
    private long tokenValidityInMillisecondsForRememberMe;

    @Override
    public void afterPropertiesSet() {
        byte[] keyBytes;
        String secret = applicationProperties.getSecurity().getAuthentication().getJwt().getSecret();
        String base64Secret = applicationProperties.getSecurity().getAuthentication().getJwt().getBase64Secret();
        if (StringUtils.isEmpty(secret) && StringUtils.isEmpty(base64Secret)) {
            throw new IllegalStateException(
                "No JWT signing key is configured. Set `application.security.authentication.jwt.base64-secret` " +
                "to a Base64-encoded value of at least 64 bytes (512 bits) — generate one with " +
                "`openssl rand -base64 64`. In a deployment, supply it through the environment variable " +
                "APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET or the legacy alias " +
                "JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET. The plain " +
                "`application.security.authentication.jwt.secret` field is an accepted alternative.");
        }
        if (!StringUtils.isEmpty(secret)) {
            log.warn("Warning: the JWT key used is not Base64-encoded. " +
                "We recommend using the `application.security.authentication.jwt.base64-secret` key for optimum security.");
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        } else {
            log.debug("Using a Base64-encoded JWT secret key");
            keyBytes = Decoders.BASE64.decode(base64Secret);
        }
        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                "The configured JWT signing key is too short: " + keyBytes.length + " bytes decoded, but the " +
                "HS512 signature algorithm requires at least 64 bytes (512 bits). Generate a longer key with " +
                "`openssl rand -base64 64` and set it via `application.security.authentication.jwt.base64-secret` " +
                "(APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET or " +
                "JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET).");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.tokenValidityInMilliseconds =
            1000 * applicationProperties.getSecurity().getAuthentication().getJwt().getTokenValidityInSeconds();
        this.tokenValidityInMillisecondsForRememberMe =
            1000 * applicationProperties.getSecurity().getAuthentication().getJwt()
                .getTokenValidityInSecondsForRememberMe();
    }

    public String createToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date validity;
        if (rememberMe) {
            validity = new Date(now + this.tokenValidityInMillisecondsForRememberMe);
        } else {
            validity = new Date(now + this.tokenValidityInMilliseconds);
        }

        return Jwts.builder()
            .subject(authentication.getName())
            .claim(AUTHORITIES_KEY, authorities)
            .signWith(key, Jwts.SIG.HS512)
            .expiration(validity)
            .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        Collection<? extends GrantedAuthority> authorities =
            Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                .filter(a -> !a.trim().isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        User principal = new User(claims.getSubject(), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(authToken);
            return true;
        } catch (ExpiredJwtException e) {
            this.securityMetersService.trackTokenExpired();
            log.info("Expired JWT token.");
        } catch (UnsupportedJwtException e) {
            this.securityMetersService.trackTokenUnsupported();
            log.info("Unsupported JWT token.");
        } catch (MalformedJwtException e) {
            this.securityMetersService.trackTokenMalformed();
            log.info("Invalid JWT token.");
        } catch (SignatureException e) {
            this.securityMetersService.trackTokenInvalidSignature();
            log.info("Invalid JWT token signature.");
        } catch (IllegalArgumentException e) {
            log.info("JWT token compact of handler are invalid.");
        }
        return false;
    }
}
