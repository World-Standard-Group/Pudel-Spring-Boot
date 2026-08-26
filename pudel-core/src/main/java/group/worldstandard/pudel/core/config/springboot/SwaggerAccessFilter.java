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

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter that gates Swagger UI and OpenAPI docs behind admin crypto challenge authentication.
 * <p>
 * Access requires a valid {@code pudel-swagger-session} cookie containing a JWT
 * issued after completing the mutual RSA admin authentication flow.
 * <p>
 * <b>Flow:</b>
 * <ol>
 *   <li>Admin completes crypto challenge → receives AdminJWT</li>
 *   <li>Admin calls {@code POST /api/admin/swagger/authorize} with AdminJWT</li>
 *   <li>Server validates and sets {@code pudel-swagger-session} HttpOnly cookie</li>
 *   <li>Admin opens Swagger UI → this filter validates the cookie → access granted</li>
 * </ol>
 * <p>
 * Can be disabled by setting {@code pudel.swagger.access-protected=false}.
 * <p>
 * Query-parameter token support ({@code ?swaggerToken=...}) is disabled by default because
 * URLs leak via browser history, server logs, proxy logs, and {@code Referer} headers.
 * Enable it explicitly with {@code pudel.swagger.allow-query-token=true}; tokens passed
 * this way are validated against a much shorter TTL ({@link #SWAGGER_QUERY_TOKEN_EXPIRY_MS}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SwaggerAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SwaggerAccessFilter.class);

    public static final String SWAGGER_SESSION_COOKIE = "pudel-swagger-session";
    public static final String SWAGGER_SESSION_SUBJECT = "pudel-swagger-session";
    public static final long SWAGGER_SESSION_EXPIRY_MS = 60 * 60 * 1000; // 1 hour

    /**
     * Subject claim value for admin session JWTs (accepted via Authorization header).
     */
    private static final String ADMIN_SESSION_SUBJECT = "pudel-admin-session";

    /**
     * Subject claim value used for short-lived query-parameter tokens.
     */
    public static final String SWAGGER_QUERY_TOKEN_SUBJECT = "pudel-swagger-query";

    /**
     * Maximum lifetime for tokens delivered via query parameter (30 seconds).
     * Kept intentionally very short to limit the window of exposure.
     */
    public static final long SWAGGER_QUERY_TOKEN_EXPIRY_MS = 30 * 1000; // 30 seconds

    private final JwtUtil jwtUtil;

    @Value("${pudel.swagger.access-protected:true}")
    private boolean accessProtected;

    /**
     * Whether query-parameter token authentication ({@code ?swaggerToken=...}) is allowed.
     * Disabled by default — URLs can leak via logs, browser history, proxies, and Referer headers.
     */
    @Value("${pudel.swagger.allow-query-token:false}")
    private boolean allowQueryToken;

    public SwaggerAccessFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Processes the incoming request to enforce access control for Swagger UI endpoints.
     * This filter checks for valid authentication tokens in the following order:
     * 1. Cookie-based authentication using a secure HTTP-only cookie.
     * 2. Authorization header authentication (Bearer or DPoP) for XHR/fetch requests.
     * 3. Query parameter authentication (only if explicitly allowed).
     * If none of the above provide a valid token, an unauthorized response is sent,
     * either in HTML or JSON format depending on the Accept header.
     *
     * @param request the HTTP servlet request containing client data
     * @param response the HTTP servlet response to be sent back to the client
     * @param filterChain the filter chain to proceed with the request processing
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // If protection is disabled, pass through
        if (!accessProtected) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Cookie-based auth (preferred — HttpOnly, Secure, SameSite)
        String cookieToken = extractTokenFromCookie(request);
        if (cookieToken != null) {
            Claims claims = jwtUtil.getClaimsFromToken(cookieToken);
            if (claims != null && (SWAGGER_SESSION_SUBJECT.equals(claims.getSubject())
                    || ADMIN_SESSION_SUBJECT.equals(claims.getSubject()))) {
                log.debug("Swagger access granted via cookie for admin: {}", claims.get("discordUserId"));
                filterChain.doFilter(request, response);
                return;
            }
            log.warn("Invalid swagger session cookie presented");
        }

        // 2. Authorization header auth (for XHR/fetch requests from the Vue admin panel)
        //    Accepts both swagger-session and admin-session JWTs so the frontend can
        //    fetch the OpenAPI spec using the existing admin token.
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && (authHeader.startsWith("Bearer ") || authHeader.startsWith("DPoP "))) {
            String headerToken = authHeader.substring(authHeader.indexOf(' ') + 1);
            if (!headerToken.isBlank()) {
                Claims claims = jwtUtil.getClaimsFromToken(headerToken);
                if (claims != null) {
                    String subject = claims.getSubject();
                    if (SWAGGER_SESSION_SUBJECT.equals(subject) || ADMIN_SESSION_SUBJECT.equals(subject)) {
                        log.debug("Swagger access granted via Authorization header for admin: {}",
                                claims.get("discordUserId"));
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
                log.warn("Invalid token in Authorization header for Swagger access");
            }
        }

        // 3. Query-parameter auth (opt-in only — URLs leak via logs, history, proxies, Referer)
        if (allowQueryToken) {
            String queryToken = request.getParameter("swaggerToken");
            if (queryToken != null) {
                Claims claims = jwtUtil.getClaimsFromToken(queryToken);
                if (claims != null && SWAGGER_QUERY_TOKEN_SUBJECT.equals(claims.getSubject())) {
                    log.warn("Swagger access granted via query-parameter token for admin: {} — "
                                    + "consider switching to cookie-based auth to avoid URL token leakage",
                            claims.get("discordUserId"));
                    filterChain.doFilter(request, response);
                    return;
                }
                log.warn("Invalid or expired swagger query-parameter token presented");
            }
        }

        // Unauthorized - return appropriate response based on Accept header
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            sendHtmlUnauthorized(response);
        } else {
            sendJsonUnauthorized(response);
        }
    }

    /**
     * Determines whether the given request should bypass the filter.
     * <p>
     * This method allows CORS preflight requests (HTTP OPTIONS) to pass through without filtering,
     * as they do not carry credentials and need to reach the CorsFilter to provide the correct headers.
     * Additionally, it exempts Swagger UI and OpenAPI documentation paths from filtering to allow
     * unrestricted access to API documentation resources.
     *
     * @param request the HTTP servlet request to evaluate
     * @return {@code true} if the request should not be filtered; {@code false} otherwise
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Always allow CORS preflight requests through — they carry no credentials and
        // must reach Spring's CorsFilter so the browser receives the correct CORS headers.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        // Only filter Swagger UI and OpenAPI docs paths
        return !path.startsWith("/swagger-ui")
                && !path.startsWith("/v3/api-docs");
    }

    /**
     * Extracts the authentication token from the cookies present in the HTTP request.
     * This method searches for a cookie with the name specified by the constant SWAGGER_SESSION_COOKIE.
     * If such a cookie is found, its value is returned; otherwise, null is returned.
     *
     * @param request the HTTP servlet request containing the cookies to search
     * @return the value of the cookie named SWAGGER_SESSION_COOKIE if present, otherwise null
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SWAGGER_SESSION_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Sends an HTTP 401 Unauthorized response with a JSON payload indicating that Swagger UI access
     * requires admin authentication via a crypto challenge.
     * The response includes structured information about the authentication flow, along with relevant endpoints.
     *
     * @param response the HttpServletResponse object used to send the JSON response
     * @throws IOException if an I/O error occurs while writing the response
     */
    private void sendJsonUnauthorized(HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("""
                {
                  "error": "Unauthorized",
                  "message": "Swagger UI access requires admin authentication via crypto challenge.",
                  "flow": [
                    "1. Complete admin mutual RSA authentication to get AdminJWT",
                    "2. POST /api/admin/swagger/authorize with Authorization: Bearer <AdminJWT>",
                    "3. Use the returned cookie or URL to access Swagger UI"
                  ],
                  "challengeEndpoint": "/api/admin/challenge",
                  "authorizeEndpoint": "/api/admin/swagger/authorize"
                }
                """);
    }

    /**
     * Sends an HTTP 401 Unauthorized response with HTML content indicating that Swagger UI access
     * requires admin authentication via a crypto challenge.
     * The response includes styled HTML with instructions on how to authenticate,
     * including relevant endpoints and steps to obtain access.
     *
     * @param response the HttpServletResponse object used to send the HTML response
     * @throws IOException if an I/O error occurs while writing the response
     */
    private void sendHtmlUnauthorized(HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Swagger UI - Authentication Required</title>
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            display: flex; justify-content: center; align-items: center;
                            min-height: 100vh; margin: 0;
                            background: #1a1a2e; color: #e0e0e0;
                        }
                        .card {
                            background: #16213e; border-radius: 12px; padding: 2.5rem;
                            max-width: 520px; width: 90%; box-shadow: 0 8px 32px rgba(0,0,0,.4);
                            border: 1px solid #0f3460;
                        }
                        h1 { color: #e94560; margin-top: 0; font-size: 1.5rem; }
                        .lock { font-size: 3rem; text-align: center; margin-bottom: 1rem; }
                        ol { padding-left: 1.2rem; line-height: 1.8; }
                        code {
                            background: #0f3460; padding: 2px 6px; border-radius: 4px;
                            font-size: 0.85rem;
                        }
                        .endpoint {
                            background: #0f3460; border-radius: 8px; padding: 1rem;
                            margin-top: 1rem; font-family: monospace; font-size: 0.85rem;
                            word-break: break-all;
                        }
                        .badge {
                            display: inline-block; padding: 2px 8px; border-radius: 4px;
                            font-size: 0.75rem; font-weight: bold; margin-right: 4px;
                        }
                        .get { background: #61affe; color: #fff; }
                        .post { background: #49cc90; color: #fff; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="lock">\uD83D\uDD10</div>
                        <h1>Authentication Required</h1>
                        <p>Swagger UI is protected by admin crypto challenge authentication.</p>
                        <ol>
                            <li>Complete the <strong>mutual RSA authentication</strong> flow to obtain an AdminJWT</li>
                            <li>Authorize Swagger access:
                                <div class="endpoint">
                                    <span class="badge post">POST</span> /api/admin/swagger/authorize<br>
                                    Authorization: Bearer &lt;AdminJWT&gt;
                                </div>
                            </li>
                            <li>Use the returned session cookie to access this page</li>
                        </ol>
                        <p style="margin-top:1.5rem; font-size:0.85rem; color:#888;">
                            Challenge endpoint:
                            <span class="badge get">GET</span>
                            <code>/api/admin/challenge</code>
                        </p>
                    </div>
                </body>
                </html>
                """);
    }
}

