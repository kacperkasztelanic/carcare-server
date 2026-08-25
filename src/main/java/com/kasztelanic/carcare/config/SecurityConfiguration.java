package com.kasztelanic.carcare.config;

import com.kasztelanic.carcare.security.AuthoritiesConstants;
import com.kasztelanic.carcare.security.ProblemDetailAccessDeniedHandler;
import com.kasztelanic.carcare.security.ProblemDetailAuthenticationEntryPoint;
import com.kasztelanic.carcare.security.jwt.JwtFilter;
import com.kasztelanic.carcare.security.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final TokenProvider tokenProvider;
    private final CorsFilter corsFilter;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailAccessDeniedHandler accessDeniedHandler;
    private final ApplicationProperties applicationProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
            .requestMatchers(new AntPathRequestMatcher("/**", HttpMethod.OPTIONS.name()))
            .requestMatchers(new AntPathRequestMatcher("/app/**/*.{js,html}"))
            .requestMatchers(new AntPathRequestMatcher("/i18n/**"))
            .requestMatchers(new AntPathRequestMatcher("/content/**"))
            .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**"))
            .requestMatchers(new AntPathRequestMatcher("/test/**"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .headers(headers -> {
                headers.contentSecurityPolicy(csp -> csp.policyDirectives(applicationProperties.getSecurity().getContentSecurityPolicy()));
                headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.permissionsPolicy(permissions -> permissions.policy("camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"));
                headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
            })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(new AntPathRequestMatcher("/api/register")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/activate")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/authenticate")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/account/reset-password/init")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/account/reset-password/finish")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/management/health")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/management/info")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/management/prometheus")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/management/**")).hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .httpBasic(Customizer.withDefaults());
        // @formatter:on
        return http.build();
    }
}
