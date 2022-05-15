package com.kasztelanic.carcare.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kasztelanic.carcare.security.jwt.JwtFilter;
import com.kasztelanic.carcare.security.jwt.TokenProvider;
import com.kasztelanic.carcare.web.rest.vm.LoginVm;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * Controller to authenticate users.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserJwtController {

    private final TokenProvider tokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    @PostMapping("/authenticate")
    public ResponseEntity<JwtToken> authorize(@Valid @RequestBody LoginVm loginVm) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginVm.getUsername(), loginVm.getPassword());
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        boolean rememberMe = loginVm.getRememberMe() != null && loginVm.getRememberMe();
        String jwt = tokenProvider.createToken(authentication, rememberMe);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JwtFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
        return ResponseEntity.ok()//
            .headers(httpHeaders)//
            .body(JwtToken.of(jwt));
    }

    /**
     * Object to return as body in JWT Authentication.
     */
    @AllArgsConstructor(staticName = "of")
    @EqualsAndHashCode
    @ToString(includeFieldNames = false)
    private static class JwtToken {

        @Getter
        @Setter
        @JsonProperty("id_token")
        private String idToken;
    }
}
