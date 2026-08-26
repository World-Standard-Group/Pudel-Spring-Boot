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
package group.worldstandard.pudel.core.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import group.worldstandard.pudel.core.dto.UserDto;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for interacting with Discord API.
 */
@Service
public class DiscordAPIService {
    private static final Logger log = LoggerFactory.getLogger(DiscordAPIService.class);
    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";
    private static final String OAUTH_TOKEN_URL = "https://discord.com/api/v10/oauth2/token";
    private static final String USER_URL = DISCORD_API_BASE + "/users/@me";
    private static final String GUILDS_URL = DISCORD_API_BASE + "/users/@me/guilds";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${pudel.discord.oauth.clientId}")
    private String clientId;

    @Value("${pudel.discord.oauth.clientSecret}")
    private String clientSecret;

    @Value("${pudel.discord.oauth.redirectUri}")
    private String redirectUri;

    /**
     * Result of a Discord OAuth2 token exchange (initial code exchange or refresh).
     *
     * @param accessToken  the Discord access token
     * @param refreshToken the Discord refresh token (may be null on refresh if Discord omits it)
     * @param expiresIn    seconds until the access token expires
     */
    public record TokenResult(String accessToken, String refreshToken, long expiresIn) {}

    /**
     * Exchange an OAuth2 authorization code for tokens.
     * Returns both access and refresh tokens per RFC 6749.
     */
    public TokenResult exchangeCodeForTokens(String code) {
        try {
            log.debug("Exchanging OAuth code for tokens, redirectUri: {}", redirectUri);

            String body = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                    "&grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                    "&scope=identify+guilds";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OAUTH_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                String accessToken = json.optString("access_token", null);
                String refreshToken = json.optString("refresh_token", null);
                long expiresIn = json.optLong("expires_in", 604800L);
                return new TokenResult(accessToken, refreshToken, expiresIn);
            }

            log.error("Failed to get tokens. Status: {}, Response: {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Error exchanging code for tokens", e);
        }
        return null;
    }

    /**
     * Exchange an OAuth2 refresh token for a fresh access token (and, if Discord
     * returns a new refresh token, that as well). Per RFC 6749 §6.
     *
     * @param refreshToken the stored Discord refresh token
     * @return TokenResult with the new access token, or null on failure
     */
    public TokenResult refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        try {
            log.debug("Refreshing Discord access token");

            String body = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                    "&grant_type=refresh_token" +
                    "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8) +
                    "&scope=identify+guilds";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OAUTH_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                String accessToken = json.optString("access_token", null);
                // Discord may return a *new* refresh token on refresh; fall back to the old one.
                String newRefreshToken = json.optString("refresh_token",
                        json.has("refresh_token") ? null : refreshToken);
                long expiresIn = json.optLong("expires_in", 604800L);
                return new TokenResult(accessToken, newRefreshToken, expiresIn);
            }

            log.error("Failed to refresh token. Status: {}, Response: {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Error refreshing Discord token", e);
        }
        return null;
    }

    /** @deprecated use {@link #exchangeCodeForTokens(String)} */
    @Deprecated
    public String getAccessToken(String code) {
        TokenResult result = exchangeCodeForTokens(code);
        return result != null ? result.accessToken() : null;
    }

    /**
     * Get user information from Discord API.
     */
    public UserDto getUserInfo(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USER_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());

                UserDto user = new UserDto();
                user.setId(json.getString("id"));
                user.setUsername(json.getString("username"));
                user.setDiscriminator(json.getString("discriminator"));

                if (!json.isNull("avatar")) {
                    user.setAvatar(
                            "https://cdn.discordapp.com/avatars/" +
                                    user.getId() + "/" +
                                    json.getString("avatar") + ".png"
                    );
                }

                if (json.has("email")) {
                    user.setEmail(json.getString("email"));
                }
                if (json.has("verified")) {
                    user.setVerified(json.getBoolean("verified"));
                }

                return user;
            }

            log.error("Failed to get user info. Status: {}", response.statusCode());
        } catch (Exception e) {
            log.error("Error fetching user info", e);
        }
        return null;
    }

    /**
     * Get list of guilds for a user.
     */
    public List<Map<String, Object>> getUserGuilds(String accessToken) {
        List<Map<String, Object>> guilds = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GUILDS_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONArray jsonArray = new JSONArray(response.body());

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject guildJson = jsonArray.getJSONObject(i);
                    Map<String, Object> guild = new HashMap<>();

                    guild.put("id", guildJson.getString("id"));
                    guild.put("name", guildJson.getString("name"));
                    guild.put("owner", guildJson.getBoolean("owner"));
                    guild.put("permissions", guildJson.getLong("permissions"));

                    if (!guildJson.isNull("icon")) {
                        guild.put(
                                "icon",
                                "https://cdn.discordapp.com/icons/" +
                                        guild.get("id") + "/" +
                                        guildJson.getString("icon") + ".png"
                        );
                    }

                    guilds.add(guild);
                }
            } else {
                log.error("Failed to get user guilds. Status: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching user guilds", e);
        }

        return guilds;
    }

    /**
     * Get a specific guild information.
     */
    public Map<String, Object> getGuildInfo(String accessToken, String guildId) {
        List<Map<String, Object>> guilds = getUserGuilds(accessToken);
        return guilds.stream()
                .filter(guild -> guild.get("id").equals(guildId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Read response from HTTP connection.
     */
    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}