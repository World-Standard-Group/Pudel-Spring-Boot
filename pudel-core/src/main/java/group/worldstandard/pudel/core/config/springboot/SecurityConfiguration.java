/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.config.springboot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration class for setting up web security in the application.
 * This class defines security rules, CORS policies, and filter configurations necessary
 * for protecting API endpoints while allowing appropriate access levels based on authentication status.
 * It integrates JWT-based authentication and custom access control for Swagger documentation.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Value("${pudel.cors.allowed-origins}")
    private List<String> allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SwaggerAccessFilter swaggerAccessFilter;

    public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter,
                                 SwaggerAccessFilter swaggerAccessFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.swaggerAccessFilter = swaggerAccessFilter;
    }


    /**
     * Configures the security filter chain for the application, defining authorization rules,
     * session management, CORS integration, CSRF protection settings, and exception handling.
     * This method sets up permit-all access for specific public endpoints, requires authentication
     * for admin and user-specific routes, applies custom filters for JWT and Swagger access control,
     * and configures an authentication entry point for unauthorized requests.
     *
     * @param http the HttpSecurity object to configure security settings
     * @return the built SecurityFilterChain instance with applied configurations
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers(
                                "/api/auth/discord/**",
                                "/api/auth/refresh",
                                "/api/bot/**",
                                "/api/plugins",
                                "/api/plugins/*",
                                "/api/plugins/installed",
                                "/api/plugins/installed/*",
                                "/api/plugins/enabled",
                                // DPoP public endpoints (needed before auth for DPoP initialization)
                                "/api/dpop/key",
                                "/api/dpop/public-key",
                                "/api/dpop/thumbprint",
                                "/api/dpop/sign",
                                // Key deletion is safe to permitAll if presenter's X-DPoP-Key-Id
                                "/api/dpop/key",
                                "/api/dpop/keys",
                                // Admin public endpoints (for challenge/key fetching)
                                "/api/admin/challenge",
                                "/api/admin/public-key",
                                // SSE log stream (uses query param token auth internally)
                                "/api/admin/logs/stream",
                                // OpenAPI / Swagger UI (permitAll at Spring Security level;
                                // SwaggerAccessFilter gates actual access via admin crypto challenge cookie)
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Admin check - requires Discord OAuth token
                        .requestMatchers("/api/admin/check").authenticated()

                        // Admin mutual RSA auth - requires Discord OAuth token
                        // This is the ONLY supported admin auth method
                        .requestMatchers("/api/admin/auth/mutual").authenticated()

                        // Deprecated auth endpoints - return 410 GONE but allow access
                        .requestMatchers("/api/admin/auth", "/api/admin/auth/oauth").permitAll()


                        // Admin endpoints - require authentication
                        .requestMatchers("/api/admin/**").authenticated()

                        // User-authenticated
                        .requestMatchers(
                                "/api/auth/user/**",
                                "/api/user/**"
                        ).authenticated()

                        // Everything else
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(swaggerAccessFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(401);
                            response.getWriter().write("{\"error\": \"Unauthorized\"}");
                        })
                );

        return http.build();
    }

    /**
     * Creates and configures a CORS configuration source that defines cross-origin resource sharing policies.
     * The configuration allows specified origins, common HTTP methods, required headers for authorization,
     * exposes specific response headers, enables credentials, and sets a max age for preflight requests.
     *
     * @return a CorsConfigurationSource instance with preconfigured CORS settings applied to all paths
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "DPoP", "X-DPoP-Key-Id"));
        configuration.setExposedHeaders(Arrays.asList("DPoP-Nonce", "WWW-Authenticate"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Prevent Spring Boot from auto-registering SwaggerAccessFilter as a servlet filter.
     * It must only run inside the Spring Security filter chain (after CorsFilter),
     * so that CORS headers are always present — even on blocked/unauthorized responses.
     */
    @Bean
    public FilterRegistrationBean<SwaggerAccessFilter> disableSwaggerFilterAutoRegistration(
            SwaggerAccessFilter filter) {
        FilterRegistrationBean<SwaggerAccessFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Prevent Spring Boot from auto-registering JwtAuthenticationFilter as a servlet filter.
     * Same reason: it should only run within the security chain.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

