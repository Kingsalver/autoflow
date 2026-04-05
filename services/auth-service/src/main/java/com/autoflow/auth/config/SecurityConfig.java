package com.autoflow.auth.config;

import com.autoflow.auth.security.JwtAuthFilter;
import com.autoflow.auth.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration notes:
 *
 * - Session: STATELESS — we rely entirely on the JWT in the HttpOnly cookie.
 * - CSRF: disabled. We're using SameSite=Lax cookies which mitigate CSRF for
 *   same-origin state-changing requests. If cross-origin POSTs are ever needed,
 *   revisit this with the double-submit cookie pattern.
 * - JWT filter runs before UsernamePasswordAuthenticationFilter.
 * - OAuth2 login is handled by Spring Security; we plug in our success handler.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final AppProperties appProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(appProperties.getFrontend().getRedirectUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // required for HttpOnly cookie auth
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(request -> request.getRequestURI()
                    .startsWith(request.getContextPath() + "/actuator"))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints
                    .requestMatchers(
                            "/auth/login/**",
                            "/auth/callback/**",
                            "/auth/refresh"
                    ).permitAll()
                    // Everything else requires a valid JWT
                    .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                    // Authorization initiation: /auth/login/{registrationId} (e.g. /auth/login/github)
                    .authorizationEndpoint(e -> e.baseUri("/auth/login"))
                    // Callback after provider redirects back: /auth/callback/{registrationId}
                    .loginProcessingUrl("/auth/callback/*")
                    .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}