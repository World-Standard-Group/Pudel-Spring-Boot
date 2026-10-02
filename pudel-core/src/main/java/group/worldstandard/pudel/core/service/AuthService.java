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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.dv8tion.jda.api.JDA;
import group.worldstandard.pudel.core.config.springboot.JwtUtil;
import group.worldstandard.pudel.core.dto.OAuthCallbackResponse;
import group.worldstandard.pudel.core.dto.UserDto;
import group.worldstandard.pudel.core.entity.Guild;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.entity.DPoPKey;
import group.worldstandard.pudel.core.entity.User;
import group.worldstandard.pudel.core.entity.UserGuild;
import group.worldstandard.pudel.core.repository.GuildRepository;
import group.worldstandard.pudel.core.service.DiscordAPIService.TokenResult;
import group.worldstandard.pudel.core.repository.GuildSettingsRepository;
import group.worldstandard.pudel.core.repository.UserRepository;
import group.worldstandard.pudel.core.repository.UserGuildRepository;
import group.worldstandard.pudel.core.session.SessionAuthenticationService;
import java.time.Instant;
import java.util.*;

@Service
public class AuthService extends BaseService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final long ADMINISTRATOR_PERMISSION = 0x8L;

    private final DiscordAPIService discordAPIService;
    private final UserRepository userRepository;
    private final GuildRepository guildRepository;
    private final GuildSettingsRepository guildSettingsRepository;
    private final UserGuildRepository userGuildRepository;
    private final JwtUtil jwtUtil;
    private final DPoPService dpopService;
    private final DPoPKeyManager dpopKeyManager;
    private final SessionAuthenticationService sessionAuthenticationService;

    public AuthService(@Lazy JDA jda,
                       DiscordAPIService discordAPIService,
                       UserRepository userRepository,
                       GuildRepository guildRepository,
                       GuildSettingsRepository guildSettingsRepository,
                       UserGuildRepository userGuildRepository,
                       JwtUtil jwtUtil,
                       DPoPService dpopService,
                       DPoPKeyManager dpopKeyManager,
                       SessionAuthenticationService sessionAuthenticationService) {
        super(jda);
        this.discordAPIService = discordAPIService;
        this.userRepository = userRepository;
        this.guildRepository = guildRepository;
        this.guildSettingsRepository = guildSettingsRepository;
        this.userGuildRepository = userGuildRepository;
        this.jwtUtil = jwtUtil;
        this.dpopService = dpopService;
        this.dpopKeyManager = dpopKeyManager;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @Transactional
    public OAuthCallbackResponse handleOAuthCallback(String code, String browserKeyId) {
        try {
            if (browserKeyId == null || browserKeyId.isBlank()) {
                return null;
            }

            TokenResult tokenResult = discordAPIService.exchangeCodeForTokens(code);
            if (tokenResult == null || tokenResult.accessToken() == null) {
                log.error("Failed to exchange code for tokens");
                return null;
            }
            String accessToken = tokenResult.accessToken();
            String refreshToken = tokenResult.refreshToken();
            long expiresInSeconds = tokenResult.expiresIn();

            UserDto userDto = discordAPIService.getUserInfo(accessToken);
            if (userDto == null) {
                log.error("Failed to get user info");
                return null;
            }

            User user = userRepository.findById(userDto.getId())
                    .orElse(new User(userDto.getId(), userDto.getUsername()));

            user.setUsername(userDto.getUsername());
            user.setDiscriminator(userDto.getDiscriminator());
            user.setAvatar(userDto.getAvatar());
            user.setEmail(userDto.getEmail());
            user.setVerified(userDto.getVerified());
            user.setAccessToken(accessToken);
            if (refreshToken != null && !refreshToken.isBlank()) {
                user.setRefreshToken(refreshToken);
            }
            user.setTokenExpiresAt(Instant.now().plusSeconds(expiresInSeconds));

            userRepository.save(user);
            log.info("User saved/updated: {}", user.getId());

            List<Map<String, Object>> discordGuilds = discordAPIService.getUserGuilds(accessToken);
            syncUserGuilds(user.getId(), discordGuilds);

            Map<String, Object> claims = new HashMap<>();
            claims.put("username", user.getUsername());
            String jwtToken = jwtUtil.generateDPoPBoundToken(
                    user.getId(), claims, dpopKeyManager.getPublicKeyThumbprint(browserKeyId));
            dpopKeyManager.bindSessionKey(browserKeyId, user.getId(), jwtToken);

            OAuthCallbackResponse response = new OAuthCallbackResponse(null, userDto);
            response.setTokenType("COOKIE");
            return response;
        } catch (Exception e) {
            log.error("Error handling OAuth callback", e);
            return null;
        }
    }

    /**
     * Refreshes the server-held DPoP-bound JWT for the browser key identified only by
     * the encrypted cookie. No token is supplied by or returned to the SPA.
     */
    @Transactional
    public OAuthCallbackResponse refresh(String browserKeyId) {
        try {
            DPoPKey session = dpopKeyManager.findActiveSession(browserKeyId).orElse(null);
            if (session == null) {
                return null;
            }

            User user = userRepository.findById(session.getUserId()).orElse(null);
            if (user == null) {
                return null;
            }

            boolean discordExpired = user.getTokenExpiresAt() == null
                    || user.getTokenExpiresAt().isBefore(Instant.now().plusSeconds(300));
            if (discordExpired) {
                String refreshToken = user.getRefreshToken();
                TokenResult refreshed = discordAPIService.refreshAccessToken(refreshToken);
                if (refreshed == null || refreshed.accessToken() == null) {
                    log.warn("Discord token refresh failed for user {} (refresh token may be revoked)",
                            user.getId());
                    return null;
                }
                user.setAccessToken(refreshed.accessToken());
                if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
                    user.setRefreshToken(refreshed.refreshToken());
                }
                user.setTokenExpiresAt(Instant.now().plusSeconds(refreshed.expiresIn()));
                userRepository.save(user);

                List<Map<String, Object>> discordGuilds =
                        discordAPIService.getUserGuilds(refreshed.accessToken());
                syncUserGuilds(user.getId(), discordGuilds);
            }

            Map<String, Object> claims = new HashMap<>();
            claims.put("username", user.getUsername());
            String jwtToken = jwtUtil.generateDPoPBoundToken(
                    user.getId(), claims, dpopKeyManager.getPublicKeyThumbprint(browserKeyId));
            dpopKeyManager.storeAccessToken(browserKeyId, jwtToken);
            log.info("Refreshed the server-held BFF token for browser key {}", browserKeyId);

            UserDto userDto = new UserDto();
            userDto.setId(user.getId());
            userDto.setUsername(user.getUsername());
            userDto.setDiscriminator(user.getDiscriminator());
            userDto.setAvatar(user.getAvatar());
            OAuthCallbackResponse response = new OAuthCallbackResponse(null, userDto);
            response.setTokenType("COOKIE");
            return response;
        } catch (Exception e) {
            log.error("Error during BFF token refresh", e);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String browserKeyId) {
        return dpopKeyManager.findActiveSession(browserKeyId)
                .map(DPoPKey::getAccessToken)
                .filter(token -> token != null && jwtUtil.validateToken(token))
                .map(jwtUtil::getUserIdFromToken)
                .flatMap(userRepository::findById)
                .map(user -> {
                    UserDto dto = new UserDto();
                    dto.setId(user.getId());
                    dto.setUsername(user.getUsername());
                    dto.setDiscriminator(user.getDiscriminator());
                    dto.setAvatar(user.getAvatar());
                    dto.setEmail(user.getEmail());
                    dto.setVerified(user.getVerified());
                    return dto;
                })
                .orElse(null);
    }

    public void revokeToken(String token) {
        log.info("Token revocation requested (server-held BFF session)");
    }

    @Transactional
    protected void syncUserGuilds(String userId, List<Map<String, Object>> discordGuilds) {
        try {
            List<UserGuild> existingUserGuilds = userGuildRepository.findByUserId(userId);
            Set<String> discordGuildIds = new HashSet<>();

            for (Map<String, Object> guildData : discordGuilds) {
                discordGuildIds.add((String) guildData.get("id"));
            }

            for (UserGuild existingUserGuild : existingUserGuilds) {
                if (!discordGuildIds.contains(existingUserGuild.getGuildId())) {
                    userGuildRepository.delete(existingUserGuild);
                }
            }

            Set<String> existingGuildIds = new HashSet<>();
            for (UserGuild ug : existingUserGuilds) {
                existingGuildIds.add(ug.getGuildId());
            }

            for (Map<String, Object> guildData : discordGuilds) {
                String guildId = (String) guildData.get("id");
                Boolean owner = (Boolean) guildData.get("owner");
                Long permissions = (Long) guildData.get("permissions");

                if (!guildRepository.existsById(guildId)) {
                    Guild guild = new Guild(guildId, (String) guildData.get("name"));
                    if (guildData.get("icon") != null) {
                        guild.setIcon((String) guildData.get("icon"));
                    }
                    guildRepository.save(guild);
                }

                if (!existingGuildIds.contains(guildId)) {
                    UserGuild userGuild = new UserGuild(userId, guildId, owner, permissions);
                    userGuildRepository.save(userGuild);
                } else {
                    Optional<UserGuild> existingOpt = userGuildRepository.findByUserIdAndGuildId(userId, guildId);
                    if (existingOpt.isPresent()) {
                        UserGuild existing = existingOpt.get();
                        existing.setOwner(owner);
                        existing.setPermissions(permissions);
                        userGuildRepository.save(existing);
                    }
                }
            }

            log.info("Synchronized {} guilds for user {}", discordGuilds.size(), userId);
        } catch (Exception e) {
            log.error("Error synchronizing user guilds", e);
            throw new RuntimeException("Failed to synchronize user guilds", e);
        }
    }

    public Map<String, Object> getUserGuilds(String userId) {
        try {
            List<UserGuild> userGuilds = userGuildRepository.findByUserId(userId);
            List<Map<String, Object>> managedGuilds = new ArrayList<>();
            List<Map<String, Object>> availableGuilds = new ArrayList<>();
            int filteredCount = 0;

            for (UserGuild ug : userGuilds) {
                Long permissions = ug.getPermissions();
                boolean hasAdminPermission = ug.getOwner() || (permissions != null && (permissions & ADMINISTRATOR_PERMISSION) != 0);

                if (!hasAdminPermission) {
                    continue;
                }

                filteredCount++;
                Optional<Guild> guildOpt = guildRepository.findById(ug.getGuildId());
                if (guildOpt.isPresent()) {
                    Guild guild = guildOpt.get();
                    boolean hasBot = isBotInGuild(guild.getId());

                    Map<String, Object> guildInfo = new HashMap<>();
                    guildInfo.put("id", guild.getId());
                    guildInfo.put("name", guild.getName());
                    guildInfo.put("icon", guild.getIcon());
                    guildInfo.put("owner", ug.getOwner());
                    guildInfo.put("permissions", ug.getPermissions());
                    guildInfo.put("hasBot", hasBot);
                    guildInfo.put("memberCount", guild.getMemberCount());

                    if (hasBot) {
                        managedGuilds.add(guildInfo);
                    } else {
                        availableGuilds.add(guildInfo);
                    }
                }
            }

            log.info("User {} has {} managed guilds and {} available guilds (filtered from {} total)",
                    userId,
                    managedGuilds.size(),
                    availableGuilds.size(),
                    filteredCount
            );

            List<Map<String, Object>> allGuilds = new ArrayList<>(managedGuilds);
            allGuilds.addAll(availableGuilds);

            return Map.of(
                    "guilds", allGuilds,
                    "managed", managedGuilds,
                    "available", availableGuilds,
                    "managedCount", managedGuilds.size(),
                    "availableCount", availableGuilds.size(),
                    "total", managedGuilds.size() + availableGuilds.size()
            );
        } catch (Exception e) {
            log.error("Error fetching user guilds", e);
            return Map.of(
                    "guilds", new ArrayList<>(),
                    "managed", new ArrayList<>(),
                    "available", new ArrayList<>(),
                    "managedCount", 0,
                    "availableCount", 0,
                    "total", 0
            );
        }
    }

    public Map<String, Object> getGuildInfo(String userId, String guildId) {
        try {
            if (!userGuildRepository.existsByUserIdAndGuildId(userId, guildId)) {
                log.warn("User {} does not have access to guild {}", userId, guildId);
                return null;
            }

            Optional<Guild> guildOpt = guildRepository.findById(guildId);
            Optional<UserGuild> userGuildOpt = userGuildRepository.findByUserIdAndGuildId(userId, guildId);
            if (guildOpt.isPresent() && userGuildOpt.isPresent()) {
                Guild guild = guildOpt.get();
                UserGuild userGuild = userGuildOpt.get();
                boolean hasBot = isBotInGuild(guild.getId());

                Map<String, Object> guildInfo = new HashMap<>();
                guildInfo.put("id", guild.getId());
                guildInfo.put("name", guild.getName());
                guildInfo.put("icon", guild.getIcon());
                guildInfo.put("owner", userGuild.getOwner());
                guildInfo.put("permissions", userGuild.getPermissions());
                guildInfo.put("hasBot", hasBot);
                guildInfo.put("memberCount", guild.getMemberCount());
                guildInfo.put("ownerId", guild.getOwnerId());

                Optional<GuildSettings> settingsOpt = guildSettingsRepository.findByGuildId(guildId);
                settingsOpt.ifPresent(guildSettings -> guildInfo.put("settings", guildSettings));
                return guildInfo;
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching guild info for user {} and guild {}", userId, guildId, e);
            return null;
        }
    }

    public void createDefaultGuildSettings(String guildId) {
        try {
            if (guildSettingsRepository.findByGuildId(guildId).isEmpty()) {
                GuildSettings settings = new GuildSettings(guildId);
                guildSettingsRepository.save(settings);
                log.info("Created default settings for guild: {}", guildId);
            }
        } catch (Exception e) {
            log.error("Error creating default guild settings", e);
        }
    }
}