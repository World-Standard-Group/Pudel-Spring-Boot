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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration for Pudel REST API.
 * <p>
 * Provides the OpenAPI 3.0 JSON spec at {@code /v3/api-docs} and the Swagger UI
 * at {@code /swagger-ui.html}.
 * <p>
 * <b>Read-only documentation.</b> These endpoints exist for route lookup only.
 * Swagger UI's "Try it out" is disabled in {@code application.yml}
 * ({@code springdoc.swagger-ui.supported-submit-methods: []}) so the spec cannot be
 * used to execute requests against the production database.
 * <p>
 * <b>Cookie-only authentication.</b> The published spec describes the current BFF
 * architecture: the single browser credential is an AES-GCM encrypted HttpOnly
 * session cookie. No {@code Authorization} header, bearer token, or client-side
 * DPoP scheme is declared, because {@link JwtAuthenticationFilter} rejects any
 * request carrying an {@code Authorization} header outright.
 */
@Configuration
public class OpenApiConfig {
    @Value("${pudel.branding.name:Pudel}")
    private String name;
    @Value("${pudel.branding.version:1.0.0}")
    private String version;

    @Bean
    public OpenAPI pudelOpenAPI() {
        final String sessionCookieSchemeName = "SessionCookie";

        return new OpenAPI()
                .info(new Info()
                        .title(name + " Discord Bot API")
                        .description("""
                                REST API for the %s Discord Bot management platform.

                                ## Authentication
                                This API is cookie-only. There is no `Authorization` header, no bearer
                                token, and no client-side DPoP proof.

                                The single credential is an AES-GCM encrypted `HttpOnly` / `Secure` /
                                `SameSite=Strict` cookie (`pudel_session`) whose plaintext is an opaque
                                database key id. Call `GET /api/session/bootstrap` once to obtain it, then
                                send it with every request (`credentials: include` in the browser,
                                `curl -b` on the command line).

                                Requests carrying an `Authorization` header are rejected with
                                `401 invalid_token` by design, so no header-based security scheme is
                                declared here.

                                ## This document is read-only
                                These docs exist for route lookup. "Try it out" is disabled, because
                                executing these endpoints against the production database is not a
                                supported use.
                                """.formatted(name))
                        .version(version)
                        .contact(new Contact()
                                .name("World Standard Group")
                                .email("it.department@worldstandard.group")
                                .url("https://worldstandard.group"))
                        .license(new License()
                                .name("AGPL-3.0 with Plugin Exception")
                                .url("https://github.com/World-Standard-Group/Pudel-Spring-Boot/blob/main/LICENSE")))
                .servers(List.of(
                        new Server().url("/").description("Current Server")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(sessionCookieSchemeName))
                .components(new Components()
                        .addSecuritySchemes(sessionCookieSchemeName, new SecurityScheme()
                                .name(sessionCookieSchemeName)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .description("AES-GCM encrypted HttpOnly browser session cookie "
                                        + "(`pudel_session`), obtained from `GET /api/session/bootstrap`. "
                                        + "The cookie is the only credential; there is no bearer token.")));
    }
}

