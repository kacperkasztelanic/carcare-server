package com.kasztelanic.carcare.security.jwt;

import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.management.SecurityMetersService;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenProviderTest {

    private static final long ONE_MINUTE = 60000;

    // 64 decoded bytes — the minimum HS512 accepts.
    private static final String VALID_BASE64_SECRET =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+Pw==";
    // 32 decoded bytes — passes Keys.hmacShaKeyFor but is rejected by HS512.
    private static final String SHORT_BASE64_SECRET = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    private SecretKey key;
    private TokenProvider tokenProvider;

    @BeforeEach
    void setup() {
        SecurityMetersService securityMetersService = new SecurityMetersService(new SimpleMeterRegistry());
        tokenProvider = new TokenProvider(securityMetersService, new ApplicationProperties());
        key = Keys.hmacShaKeyFor(Decoders.BASE64
            .decode("fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"));

        ReflectionTestUtils.setField(tokenProvider, "key", key);
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", ONE_MINUTE);
    }

    private static TokenProvider newTokenProvider(ApplicationProperties properties) {
        return new TokenProvider(new SecurityMetersService(new SimpleMeterRegistry()), properties);
    }

    @Test
    void afterPropertiesSetThrowsWhenNoKeyIsConfigured() {
        TokenProvider provider = newTokenProvider(new ApplicationProperties());

        assertThatThrownBy(provider::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("application.security.authentication.jwt.base64-secret");
    }

    @Test
    void afterPropertiesSetThrowsWhenBase64KeyIsTooShort() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSecurity().getAuthentication().getJwt().setBase64Secret(SHORT_BASE64_SECRET);
        TokenProvider provider = newTokenProvider(properties);

        assertThatThrownBy(provider::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("64 bytes");
    }

    @Test
    void afterPropertiesSetAcceptsAValidBase64Key() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSecurity().getAuthentication().getJwt().setBase64Secret(VALID_BASE64_SECRET);
        TokenProvider provider = newTokenProvider(properties);

        assertThatCode(provider::afterPropertiesSet).doesNotThrowAnyException();

        String token = provider.createToken(createAuthentication(), false);
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void afterPropertiesSetAcceptsThePlainSecret() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSecurity().getAuthentication().getJwt()
            .setSecret("plain-secret-long-enough-for-hs512-plain-secret-long-enough-for-hs512");
        TokenProvider provider = newTokenProvider(properties);

        assertThatCode(provider::afterPropertiesSet).doesNotThrowAnyException();

        String token = provider.createToken(createAuthentication(), false);
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void testReturnFalseWhenJWThasInvalidSignature() {
        boolean isTokenValid = tokenProvider.validateToken(createTokenWithDifferentSignature());

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisMalformed() {
        Authentication authentication = createAuthentication();
        String token = tokenProvider.createToken(authentication, false);
        String invalidToken = token.substring(1);
        boolean isTokenValid = tokenProvider.validateToken(invalidToken);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisExpired() {
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", -ONE_MINUTE);

        Authentication authentication = createAuthentication();
        String token = tokenProvider.createToken(authentication, false);

        boolean isTokenValid = tokenProvider.validateToken(token);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisUnsupported() {
        String unsupportedToken = createUnsupportedToken();

        boolean isTokenValid = tokenProvider.validateToken(unsupportedToken);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisInvalid() {
        boolean isTokenValid = tokenProvider.validateToken("");

        assertThat(isTokenValid).isFalse();
    }

    private Authentication createAuthentication() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(AuthoritiesConstants.ANONYMOUS));
        return new UsernamePasswordAuthenticationToken("anonymous", "anonymous", authorities);
    }

    private String createUnsupportedToken() {
        return Jwts.builder()
            .content("payload")
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    private String createTokenWithDifferentSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Decoders.BASE64
            .decode("Xfd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"));

        return Jwts.builder()
            .subject("anonymous")
            .signWith(otherKey, Jwts.SIG.HS512)
            .expiration(new Date(new Date().getTime() + ONE_MINUTE))
            .compact();
    }
}
