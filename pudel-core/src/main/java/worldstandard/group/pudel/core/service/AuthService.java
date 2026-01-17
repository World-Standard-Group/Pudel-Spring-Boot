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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.dv8tion.jda.api.JDA;
import worldstandard.group.pudel.core.config.JwtUtil;
import worldstandard.group.pudel.core.dto.OAuthCallbackResponse;
import worldstandard.group.pudel.core.dto.UserDto;
import worldstandard.group.pudel.core.entity.Guild;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.entity.User;
import worldstandard.group.pudel.core.entity.UserGuild;
import worldstandard.group.pudel.core.repository.GuildRepository;
import worldstandard.group.pudel.core.repository.GuildSettingsRepository;
import worldstandard.group.pudel.core.repository.UserRepository;
import worldstandard.group.pudel.core.repository.UserGuildRepository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for authentication operations.
 */
@Service
public class AuthService extends BaseService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final DiscordAPIService discordAPIService;
    private final UserRepository userRepository;
    private final GuildRepository guildRepository;
    private final GuildSettingsRepository guildSettingsRepository;
    private final UserGuildRepository userGuildRepository;
    private final JwtUtil jwtUtil;

    public AuthService(JDA jda,
                      DiscordAPIService discordAPIService,
                      UserRepository userRepository,
                      GuildRepository guildRepository,
                      GuildSettingsRepository guildSettingsRepository,
                      UserGuildRepository userGuildRepository,
                      JwtUtil jwtUtil) {
        super(jda);
        this.discordAPIService = discordAPIService;
        this.userRepository = userRepository;
        this.guildRepository = guildRepository;
        this.guildSettingsRepository = guildSettingsRepository;
        this.userGuildRepository = userGuildRepository;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Handle Discord OAuth callback.
     */
    @Transactional
    public OAuthCallbackResponse handleOAuthCallback(String code) {
        try {
            // Exchange code for access token
            String accessToken = discordAPIService.getAccessToken(code);
            if (accessToken == null) {
                log.error("Failed to get access token");
                return null;
            }

            // Get user information
            UserDto userDto = discordAPIService.getUserInfo(accessToken);
            if (userDto == null) {
                log.error("Failed to get user info");
                return null;
            }

            // Save or update user in database
            User user = userRepository.findById(userDto.getId())
                    .orElse(new User(userDto.getId(), userDto.getUsername()));

            user.setUsername(userDto.getUsername());
            user.setDiscriminator(userDto.getDiscriminator());
            user.setAvatar(userDto.getAvatar());
            user.setEmail(userDto.getEmail());
            user.setVerified(userDto.getVerified());
            user.setAccessToken(accessToken);
            user.setTokenExpiresAt(LocalDateTime.now().plusHours(24));

            userRepository.save(user);
            log.info("User saved/updated: {}", user.getId());

            // Fetch and store user's guilds
            List<Map<String, Object>> discordGuilds = discordAPIService.getUserGuilds(accessToken);
            syncUserGuilds(user.getId(), discordGuilds);

            // Generate JWT token
            Map<String, Object> claims = new HashMap<>();
            claims.put("username", user.getUsername());
            String jwtToken = jwtUtil.generateToken(user.getId(), claims);

            return new OAuthCallbackResponse(jwtToken, userDto);
        } catch (Exception e) {
            log.error("Error handling OAuth callback", e);
            return null;
        }
    }

    /**
     * Synchronize user's Discord guilds with database.
     */
    @Transactional
    protected void syncUserGuilds(String userId, List<Map<String, Object>> discordGuilds) {
        try {
            // Get existing user-guild associations
            List<UserGuild> existingUserGuilds = userGuildRepository.findByUserId(userId);

            // Create a set of guild IDs from Discord
            Set<String> discordGuildIds = new HashSet<>();
            for (Map<String, Object> guildData : discordGuilds) {
                discordGuildIds.add((String) guildData.get("id"));
            }

            // Remove associations for guilds no longer in Discord
            for (UserGuild existingUserGuild : existingUserGuilds) {
                if (!discordGuildIds.contains(existingUserGuild.getGuildId())) {
                    userGuildRepository.delete(existingUserGuild);
                    log.debug("Removed user-guild association for user {} and guild {}", userId, existingUserGuild.getGuildId());
                }
            }

            // Create a set of existing guild IDs for quick lookup
            Set<String> existingGuildIds = new HashSet<>();
            for (UserGuild ug : existingUserGuilds) {
                existingGuildIds.add(ug.getGuildId());
            }

            // Process each Discord guild
            for (Map<String, Object> guildData : discordGuilds) {
                String guildId = (String) guildData.get("id");
                Boolean owner = (Boolean) guildData.get("owner");
                Long permissions = (Long) guildData.get("permissions");

                // Ensure guild exists in database
                if (!guildRepository.existsById(guildId)) {
                    Guild guild = new Guild(guildId, (String) guildData.get("name"));
                    if (guildData.get("icon") != null) {
                        guild.setIcon((String) guildData.get("icon"));
                    }
                    guildRepository.save(guild);
                    log.debug("Created guild record for: {}", guildId);
                }

                // Create or update user-guild association
                if (!existingGuildIds.contains(guildId)) {
                    UserGuild userGuild = new UserGuild(userId, guildId, owner, permissions);
                    userGuildRepository.save(userGuild);
                    log.debug("Created user-guild association for user {} and guild {}", userId, guildId);
                } else {
                    // Update existing association if permissions or owner status changed
                    Optional<UserGuild> existingOpt = userGuildRepository.findByUserIdAndGuildId(userId, guildId);
                    if (existingOpt.isPresent()) {
                        UserGuild existing = existingOpt.get();
                        existing.setOwner(owner);
                        existing.setPermissions(permissions);
                        userGuildRepository.save(existing);
                        log.debug("Updated user-guild association for user {} and guild {}", userId, guildId);
                    }
                }
            }

            log.info("Synchronized {} guilds for user {}", discordGuilds.size(), userId);
        } catch (Exception e) {
            log.error("Error synchronizing user guilds", e);
            throw new RuntimeException("Failed to synchronize user guilds", e);
        }
    }

    /**
     * Get user's guilds with bot presence information.
     * Only returns guilds where user has ADMINISTRATOR permission or is the owner.
     * <p>
     * Discord ADMINISTRATOR permission = 0x8 (8)
     */
    public Map<String, Object> getUserGuilds(String userId) {
        try {
            // Discord ADMINISTRATOR permission flag
            long ADMINISTRATOR_PERMISSION = 0x8L;

            // Get user's guild associations
            List<UserGuild> userGuilds = userGuildRepository.findByUserId(userId);
            log.debug("Found {} total guilds for user: {}", userGuilds.size(), userId);

            List<Map<String, Object>> managedGuilds = new ArrayList<>();
            List<Map<String, Object>> availableGuilds = new ArrayList<>();
            int filteredCount = 0;

            for (UserGuild ug : userGuilds) {
                // Check if user has ADMINISTRATOR permission in this guild
                Long permissions = ug.getPermissions();
                boolean hasAdminPermission = ug.getOwner() || (permissions != null && (permissions & ADMINISTRATOR_PERMISSION) != 0);

                // Only include guilds where user is owner OR has admin permission
                if (!hasAdminPermission) {
                    log.debug("User {} does not have admin permission in guild {}", userId, ug.getGuildId());
                    continue;
                }

                filteredCount++;
                Optional<Guild> guildOpt = guildRepository.findById(ug.getGuildId());
                if (guildOpt.isPresent()) {
                    Guild guild = guildOpt.get();

                    // Check if bot is actually in the guild using JDA instance
                    boolean hasBot = isBotInGuild(guild.getId());

                    Map<String, Object> guildInfo = new HashMap<>();
                    guildInfo.put("id", guild.getId());
                    guildInfo.put("name", guild.getName());
                    guildInfo.put("icon", guild.getIcon());
                    guildInfo.put("owner", ug.getOwner());
                    guildInfo.put("permissions", ug.getPermissions());
                    guildInfo.put("hasBot", hasBot);
                    guildInfo.put("memberCount", guild.getMemberCount());

                    // Separate into managed (hasBot) and available (no bot)
                    if (hasBot) {
                        log.debug("Guild {} has bot for user {}", guild.getId(), userId);
                        managedGuilds.add(guildInfo);
                    } else {
                        log.debug("Guild {} available (no bot) for user {}", guild.getId(), userId);
                        availableGuilds.add(guildInfo);
                    }
                }
            }

            log.info("User {} has {} managed guilds and {} available guilds (filtered from {} total)",
                    userId, managedGuilds.size(), availableGuilds.size(), filteredCount);

            List<Map<String, Object>> allGuilds = new ArrayList<>();
            allGuilds.addAll(managedGuilds);
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

    /**
     * Get a specific guild's information for a user.
     */
    public Map<String, Object> getGuildInfo(String userId, String guildId) {
        try {
            // Verify user has access to guild
            if (!userGuildRepository.existsByUserIdAndGuildId(userId, guildId)) {
                log.warn("User {} does not have access to guild {}", userId, guildId);
                return null;
            }

            Optional<Guild> guildOpt = guildRepository.findById(guildId);
            Optional<UserGuild> userGuildOpt = userGuildRepository.findByUserIdAndGuildId(userId, guildId);

            if (guildOpt.isPresent() && userGuildOpt.isPresent()) {
                Guild guild = guildOpt.get();
                UserGuild userGuild = userGuildOpt.get();

                // Check if bot is actually in the guild using JDA instance
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

                // Get guild settings if they exist
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

    /**
     * Create default guild settings when bot joins a guild.
     */
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

