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

import group.worldstandard.pudel.core.service.DPoPService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT authentication filter that validates tokens on every request.
 * Supports both standard Bearer tokens and DPoP-bound tokens.
 * <p>
 * For DPoP-bound tokens:
 * <ul>
 *   <li>Client must include DPoP header with a signed proof</li>
 *   <li>The proof must be bound to the access token via 'ath' claim</li>
 *   <li>The token's thumbprint binding must match the proof's JWK</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String DPOP_HEADER = "DPoP";
    private static final String AUTH_SCHEME_DPOP = "DPoP";
    private static final String AUTH_SCHEME_BEARER = "Bearer";

    private final JwtUtil jwtUtil;
    private final DPoPService dpopService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, DPoPService dpopService) {
        this.jwtUtil = jwtUtil;
        this.dpopService = dpopService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String dpopProof = request.getHeader(DPOP_HEADER);

        if (authHeader != null) {
            String token = null;
            boolean isDPoP = false;

            // Determine token type and extract token
            if (authHeader.startsWith(AUTH_SCHEME_DPOP + " ")) {
                token = authHeader.substring(AUTH_SCHEME_DPOP.length() + 1);
                isDPoP = true;
            } else if (authHeader.startsWith(AUTH_SCHEME_BEARER + " ")) {
                token = authHeader.substring(AUTH_SCHEME_BEARER.length() + 1);
                // Check if it's a DPoP-bound token being used as Bearer (error)
                if (jwtUtil.isDPoPBoundToken(token)) {
                    log.warn("DPoP-bound token used with Bearer scheme - use DPoP scheme instead");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader("WWW-Authenticate", "DPoP error=\"use_dpop_nonce\", error_description=\"Token is DPoP-bound\"");
                    response.getWriter().write("{\"error\":\"invalid_token\",\"error_description\":\"DPoP-bound token must use DPoP scheme\"}");
                    return;
                }
            }

            if (token != null && jwtUtil.validateToken(token)) {
                // For DPoP tokens, validate the proof
                if (isDPoP) {
                    if (dpopProof == null) {
                        log.warn("DPoP scheme used but no DPoP proof header");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setHeader("WWW-Authenticate", "DPoP error=\"invalid_dpop_proof\"");
                        response.getWriter().write("{\"error\":\"invalid_dpop_proof\",\"error_description\":\"Missing DPoP proof\"}");
                        return;
                    }

                    // Build the request URI
                    String httpUri = request.getRequestURL().toString();
                    String httpMethod = request.getMethod();

                    // Validate DPoP proof
                    DPoPService.DPoPValidationResult proofResult =
                            dpopService.validateProofForResource(dpopProof, httpMethod, httpUri, token);

                    if (!proofResult.valid()) {
                        log.warn("DPoP proof validation failed: {}", proofResult.error());
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setHeader("WWW-Authenticate", "DPoP error=\"invalid_dpop_proof\", error_description=\"Invalid DPoP proof\"");
                        response.getWriter().write("{\"error\":\"invalid_dpop_proof\",\"error_description\":\"Invalid DPoP proof\"}");
                        return;
                    }

                    // Verify token is bound to this thumbprint
                    String tokenThumbprint = jwtUtil.getDPoPThumbprint(token);
                    if (tokenThumbprint != null && !tokenThumbprint.equals(proofResult.thumbprint())) {
                        log.warn("DPoP thumbprint mismatch: token bound to {}, proof from {}",
                                tokenThumbprint, proofResult.thumbprint());
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setHeader("WWW-Authenticate", "DPoP error=\"invalid_dpop_proof\", error_description=\"Token not bound to this key\"");
                        response.getWriter().write("{\"error\":\"invalid_dpop_proof\",\"error_description\":\"Token not bound to this key\"}");
                        return;
                    }

                    log.debug("DPoP proof validated successfully for user");
                }

                String userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    // Add DPoP-verified authority if using DPoP
                    List<SimpleGrantedAuthority> authorities = isDPoP
                            ? List.of(new SimpleGrantedAuthority("DPOP_VERIFIED"))
                            : Collections.emptyList();

                    // Create authentication token
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            userId, null, authorities
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Set authentication for user: {} (DPoP: {})", userId, isDPoP);
                }
            }
        }

        filterChain.doFilter(request, response);
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/api/auth/discord/")
                || path.startsWith("/api/bot/")
                || path.equals("/api/admin/logs/stream") // SSE uses query param token auth
                || (path.equals("/api/plugins") && "GET".equals(request.getMethod()))
                || (path.equals("/api/plugins/installed") && "GET".equals(request.getMethod()))
                || (path.matches("/api/plugins/installed/[^/]+") && "GET".equals(request.getMethod()))
                || (path.equals("/api/plugins/enabled") && "GET".equals(request.getMethod()))
                || (path.matches("/api/plugins/[^/]+") && "GET".equals(request.getMethod()));
    }
}

