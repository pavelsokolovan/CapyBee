package com.capybee.server.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.service.SessionRestoreTokenService;
import com.capybee.server.service.UserService;

@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173,https://capybee.fly.dev}")
    private String[] allowedOrigins;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationSuccessHandler oauth2SuccessHandler)
            throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionFixation().migrateSession())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/health", "/actuator/health", "/error", "/oauth2/**", "/login/**")
                        .permitAll()
                        .requestMatchers("/api/public/**", "/api/auth-status", "/api/health",
                                "/api/session/restore", "/api/session/revoke")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                (RequestMatcher) request -> request.getRequestURI().startsWith("/api/")))
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oauth2SuccessHandler))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                .build();
    }

    /**
     * Issues a localStorage-backed restore token alongside the session cookie so
     * the client can re-establish a session if the cookie itself is later
     * dropped (e.g. Android PWA killed from recents).
     */
    @Bean
    AuthenticationSuccessHandler oauth2SuccessHandler(UserService userService, SessionRestoreTokenService tokenService) {
        return (request, response, authentication) -> {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            UserAccount user = userService.getCurrentUser(oauth2Token);
            String restoreToken = tokenService.issueToken(user);
            response.sendRedirect("/#session_token=" + restoreToken);
        };
    }
}
