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
import group.worldstandard.pudel.core.entity.User;
import group.worldstandard.pudel.core.entity.UserGuild;
import group.worldstandard.pudel.core.repository.GuildRepository;
import group.worldstandard.pudel.core.service.DiscordAPIService.TokenResult;
import group.worldstandard.pudel.core.repository.GuildSettingsRepository;
import group.worldstandard.pudel.core.repository.UserRepository;
import group.worldstandard.pudel.core.repository.UserGuildRepository;
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

    public AuthService(@Lazy JDA jda,
                       DiscordAPIService discordAPIService,
                       UserRepository userRepository,
                       GuildRepository guildRepository,
                       GuildSettingsRepository guildSettingsRepository,
                       UserGuildRepository userGuildRepository,
                       JwtUtil jwtUtil,
                       DPoPService dpopService) {
        super(jda);
        this.discordAPIService = discordAPIService;
        this.userRepository = userRepository;
        this.guildRepository = guildRepository;
        this.guildSettingsRepository = guildSettingsRepository;
        this.userGuildRepository = userGuildRepository;
        this.jwtUtil = jwtUtil;
        this.dpopService = dpopService;
    }

    @Transactional
    public OAuthCallbackResponse handleOAuthCallback(String code) {
        return handleOAuthCallback(code, null, null, null, null);
    }

    @Transactional
    public OAuthCallbackResponse handleOAuthCallback(String code, String dpopProof, String httpMethod, String httpUri, String dpopKeyId) {
        try {
            String dpopThumbprint = null;

            if (dpopProof != null && !dpopProof.isBlank()) {
                // Use database-backed key validation (keyId from header)
                DPoPService.DPoPValidationResult proofResult = dpopService.validateProofForResource(dpopProof, httpMethod, httpUri, null, dpopKeyId);

                if (!proofResult.valid()) {
                    log.warn("DPoP proof validation failed during OAuth: {}", proofResult.error());
                    return null;
                }

                dpopThumbprint = proofResult.thumbprint();
                log.info("DPoP proof validated for OAuth callback, thumbprint: {}", dpopThumbprint);
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

            String jwtToken;
            String tokenType;
            if (dpopThumbprint != null) {
                jwtToken = jwtUtil.generateDPoPBoundToken(user.getId(), claims, dpopThumbprint);
                tokenType = JwtUtil.TOKEN_TYPE_DPOP;
                log.info("Generated DPoP-bound token for user: {}", user.getId());
            } else {
                jwtToken = jwtUtil.generateToken(user.getId(), claims);
                tokenType = JwtUtil.TOKEN_TYPE_BEARER;
                log.info("Generated Bearer token for user: {}", user.getId());
            }

            OAuthCallbackResponse response = new OAuthCallbackResponse(jwtToken, userDto);
            response.setTokenType(tokenType);
            return response;
        } catch (Exception e) {
            log.error("Error handling OAuth callback", e);
            return null;
        }
    }

    /**
     * Refresh a user's session WITHOUT forcing a full Discord re-login.
     * <p>
     * RFC 9449 BFF refresh flow:
     * <ol>
     *   <li>Validate the DPoP proof presented with the (still-valid) access token.</li>
     *   <li>Re-validate that the token is still bound to the SAME DPoP key thumbprint
     *       (prevents key-desync → "key mismatch 401" after a restart).</li>
     *   <li>If the Discord access token is expired/near-expiry, exchange the stored
     *       Discord <b>refresh token</b> (server-side, never sent to the client) for a
     *       fresh Discord access token and re-sync guilds.</li>
     *   <li>Re-issue a DPoP-bound JWT bound to the SAME key thumbprint — the key pair
     *       is NEVER regenerated here, so existing DPoP proofs keep validating.</li>
     * </ol>
     * This is what lets the SPA survive restarts and token expiry silently.
     *
     * @param dpopProof  the DPoP proof from the request
     * @param httpMethod the HTTP method
     * @param httpUri    the full request URI
     * @param dpopKeyId the DPoP key id (X-DPoP-Key-Id header)
     * @param existingToken the current access token (Authorization: DPoP <token>)
     * @return a fresh OAuthCallbackResponse, or null if refresh is impossible (caller forces re-login)
     */
    @Transactional
    public OAuthCallbackResponse refresh(String dpopProof, String httpMethod, String httpUri,
                                      String dpopKeyId, String existingToken) {
        try {
            if (existingToken == null || existingToken.isBlank()) {
                return null;
            }

            // 1. The current token must still be valid (signature + not expired).
            if (!jwtUtil.validateToken(existingToken)) {
                log.info("Refresh denied: existing token invalid/expired");
                return null;
            }

            // 2. Validate the DPoP proof and confirm the token is still bound to this key.
            DPoPService.DPoPValidationResult proofResult =
                    dpopService.validateProofForResource(dpopProof, httpMethod, httpUri, existingToken, dpopKeyId);
            if (!proofResult.valid()) {
                log.warn("Refresh denied: DPoP proof invalid: {}", proofResult.error());
                return null;
            }
            String tokenThumbprint = jwtUtil.getDPoPThumbprint(existingToken);
            if (tokenThumbprint != null && !tokenThumbprint.equals(proofResult.thumbprint())) {
                log.warn("Refresh denied: token not bound to presented key");
                return null;
            }

            String userId = jwtUtil.getUserIdFromToken(existingToken);
            if (userId == null) {
                return null;
            }

            // 3. Refresh the Discord access token server-side if needed.
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return null;
            }
            boolean discordExpired = user.getTokenExpiresAt() == null
                    || user.getTokenExpiresAt().isBefore(Instant.now().plusSeconds(300));
            if (discordExpired) {
                String refreshToken = user.getRefreshToken();
                TokenResult refreshed = discordAPIService.refreshAccessToken(refreshToken);
                if (refreshed == null || refreshed.accessToken() == null) {
                    log.warn("Discord token refresh failed for user {} (refresh token may be revoked)", userId);
                    return null; // caller must force full re-login
                }
                user.setAccessToken(refreshed.accessToken());
                if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
                    user.setRefreshToken(refreshed.refreshToken());
                }
                user.setTokenExpiresAt(Instant.now().plusSeconds(refreshed.expiresIn()));
                userRepository.save(user);

                List<Map<String, Object>> discordGuilds =
                        discordAPIService.getUserGuilds(refreshed.accessToken());
                syncUserGuilds(userId, discordGuilds);
            }

            // 4. Re-issue a DPoP-bound JWT bound to the SAME key (no key regeneration).
            Map<String, Object> claims = new HashMap<>();
            claims.put("username", user.getUsername());
            String jwtToken = jwtUtil.generateDPoPBoundToken(userId, claims, proofResult.thumbprint());
            log.info("Refreshed DPoP-bound token for user: {} (key unchanged)", userId);

            OAuthCallbackResponse response = new OAuthCallbackResponse(jwtToken, null);
            response.setTokenType(JwtUtil.TOKEN_TYPE_DPOP);
            return response;
        } catch (Exception e) {
            log.error("Error during token refresh", e);
            return null;
        }
    }

    public void revokeToken(String token) {
        log.info("Token revocation requested (BFF-style - handled by session management)");
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