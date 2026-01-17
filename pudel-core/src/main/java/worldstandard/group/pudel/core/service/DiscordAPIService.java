/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
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
package worldstandard.group.pudel.core.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import worldstandard.group.pudel.core.dto.UserDto;

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

    private final Gson gson = new Gson();

    /**
     * Exchange authorization code for access token.
     */
    public String getAccessToken(String code) {
        try {
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
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                return json.get("access_token").getAsString();
            }

            log.error("Failed to get access token. Status: {}", response.statusCode());
        } catch (Exception e) {
            log.error("Error exchanging code for token", e);
        }
        return null;
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
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);

                UserDto user = new UserDto();
                user.setId(json.get("id").getAsString());
                user.setUsername(json.get("username").getAsString());
                user.setDiscriminator(json.get("discriminator").getAsString());

                if (json.has("avatar") && !json.get("avatar").isJsonNull()) {
                    user.setAvatar(
                            "https://cdn.discordapp.com/avatars/" +
                                    user.getId() + "/" +
                                    json.get("avatar").getAsString() + ".png"
                    );
                }

                if (json.has("email")) {
                    user.setEmail(json.get("email").getAsString());
                }
                if (json.has("verified")) {
                    user.setVerified(json.get("verified").getAsBoolean());
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
                JsonArray jsonArray = gson.fromJson(response.body(), JsonArray.class);

                for (var element : jsonArray) {
                    JsonObject guildJson = element.getAsJsonObject();
                    Map<String, Object> guild = new HashMap<>();

                    guild.put("id", guildJson.get("id").getAsString());
                    guild.put("name", guildJson.get("name").getAsString());
                    guild.put("owner", guildJson.get("owner").getAsBoolean());
                    guild.put("permissions", guildJson.get("permissions").getAsLong());

                    if (!guildJson.get("icon").isJsonNull()) {
                        guild.put(
                                "icon",
                                "https://cdn.discordapp.com/icons/" +
                                        guild.get("id") + "/" +
                                        guildJson.get("icon").getAsString() + ".png"
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

