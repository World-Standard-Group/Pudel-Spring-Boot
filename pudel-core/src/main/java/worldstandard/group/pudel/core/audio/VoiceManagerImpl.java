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
package worldstandard.group.pudel.core.audio;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import worldstandard.group.pudel.api.audio.AudioProvider;
import worldstandard.group.pudel.api.audio.AudioReceiver;
import worldstandard.group.pudel.api.audio.DAVEProvider;
import worldstandard.group.pudel.api.audio.VoiceConnectionStatus;
import worldstandard.group.pudel.api.audio.VoiceManager;

import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of VoiceManager for handling voice connections with DAVE support.
 *
 * <p>This implementation manages voice connections and enforces DAVE requirements
 * for voice encryption starting March 1st, 2026.</p>
 */
@Service
public class VoiceManagerImpl implements VoiceManager {

    private static final Logger logger = LoggerFactory.getLogger(VoiceManagerImpl.class);

    private static final LocalDate DAVE_DEADLINE = LocalDate.of(2026, Month.MARCH, 1);
    private static final int MINIMUM_JAVA_VERSION_FOR_JDAVE = 25;
    private static final int MINIMUM_JAVA_VERSION_FOR_LIBDAVE = 8;

    private final JDA jda;

    // Per-guild audio providers and receivers
    private final Map<Long, AudioProvider> audioProviders = new ConcurrentHashMap<>();
    private final Map<Long, AudioReceiver> audioReceivers = new ConcurrentHashMap<>();

    // Per-guild connection status tracking
    private final Map<Long, VoiceConnectionStatus> connectionStatuses = new ConcurrentHashMap<>();

    // DAVE providers from plugins
    private final Map<String, DAVEProvider> daveProviders = new ConcurrentHashMap<>();
    private volatile DAVEProvider activeDAVEProvider = null;

    public VoiceManagerImpl(@Lazy JDA jda) {
        this.jda = jda;
        logger.info("VoiceManager initialized. DAVE deadline: {}", DAVE_DEADLINE);
        checkDAVEDeadlineWarning();

        // Auto-detect JDAVE from core classpath (configured in JDAConfiguration)
        detectCoreDAVE();
    }

    /**
     * Detect if JDAVE was configured at the JDA level (core classpath).
     * This is the preferred method as of Option A implementation.
     */
    private void detectCoreDAVE() {
        try {
            // Check if JDAVE is available in classpath
            Class.forName("club.minnced.discord.jdave.interop.JDaveSessionFactory");

            // Check Java version
            int javaVersion = Runtime.version().feature();
            if (javaVersion >= MINIMUM_JAVA_VERSION_FOR_JDAVE) {
                // Register a synthetic DAVE provider to indicate DAVE is available
                CoreJDAVEProvider coreProvider = new CoreJDAVEProvider();
                daveProviders.put("core-jdave", coreProvider);
                activeDAVEProvider = coreProvider;
                logger.info("Core JDAVE detected and active - voice encryption available");
            } else {
                logger.warn("JDAVE found in classpath but requires Java 25+, running Java {}", javaVersion);
            }
        } catch (ClassNotFoundException e) {
            logger.info("Core JDAVE not in classpath - plugins may provide DAVE implementation");
        }
    }

    /**
     * Synthetic DAVE provider representing the core-level JDAVE configuration.
     * This provider indicates JDAVE is configured at the JDA builder level.
     */
    private static class CoreJDAVEProvider implements DAVEProvider {
        @Override
        public String getName() {
            return "JDAVE (Core)";
        }

        @Override
        public String getVersion() {
            return "0.1.5";
        }

        @Override
        public int getRequiredJavaVersion() {
            return MINIMUM_JAVA_VERSION_FOR_JDAVE;
        }

        @Override
        public void initialize() {
            // JDAVE is initialized at JDA builder level, nothing to do here
        }

        @Override
        public boolean isAvailable() {
            return Runtime.version().feature() >= MINIMUM_JAVA_VERSION_FOR_JDAVE;
        }

        @Override
        public void shutdown() {
            // Nothing to shutdown - managed by JDA
        }

        @Override
        public Object getNativeImplementation() {
            // The native implementation is configured in JDAConfiguration via reflection
            // and attached to JDA's AudioModuleConfig. We don't expose it here.
            // Return null as the actual DAVE session factory is managed by JDA internally.
            try {
                Class<?> factoryClass = Class.forName("club.minnced.discord.jdave.interop.JDaveSessionFactory");
                return factoryClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return null;
            }
        }
    }

    private void checkDAVEDeadlineWarning() {
        LocalDate now = LocalDate.now();
        if (now.isBefore(DAVE_DEADLINE)) {
            long daysUntilDeadline = java.time.temporal.ChronoUnit.DAYS.between(now, DAVE_DEADLINE);
            if (daysUntilDeadline <= 60) {
                logger.warn("=======================================================");
                logger.warn("DAVE DEADLINE WARNING: {} days until voice E2EE required!", daysUntilDeadline);
                logger.warn("All voice connections will require DAVE after March 1st, 2026.");
                logger.warn("Ensure your audio plugins provide a DAVEProvider implementation.");
                logger.warn("See: https://discord.com/developers/docs/topics/voice-connections");
                logger.warn("=======================================================");
            }
        } else {
            logger.info("DAVE is now required for all voice connections.");
        }
    }

    @Override
    public CompletableFuture<VoiceConnectionStatus> connect(long guildId, long voiceChannelId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Guild guild = jda.getGuildById(guildId);
                if (guild == null) {
                    logger.error("Guild not found: {}", guildId);
                    return VoiceConnectionStatus.ERROR;
                }

                VoiceChannel voiceChannel = guild.getVoiceChannelById(voiceChannelId);
                if (voiceChannel == null) {
                    logger.error("Voice channel not found: {}", voiceChannelId);
                    return VoiceConnectionStatus.ERROR;
                }

                // Check DAVE requirement
                if (isDAVERequired() && !isDAVEAvailable(guildId)) {
                    logger.error("DAVE implementation required but not available. Voice connection denied.");
                    connectionStatuses.put(guildId, VoiceConnectionStatus.DAVE_REQUIRED);
                    return VoiceConnectionStatus.DAVE_REQUIRED;
                }

                // Initialize DAVE if available
                if (activeDAVEProvider != null) {
                    try {
                        activeDAVEProvider.initialize();
                        logger.info("DAVE initialized with: {} v{}",
                            activeDAVEProvider.getName(), activeDAVEProvider.getVersion());
                    } catch (DAVEProvider.DAVEException e) {
                        logger.error("Failed to initialize DAVE: {}", e.getMessage(), e);
                        connectionStatuses.put(guildId, VoiceConnectionStatus.DAVE_ERROR);
                        return VoiceConnectionStatus.DAVE_ERROR;
                    }
                }

                // Get the audio manager
                AudioManager audioManager = guild.getAudioManager();

                // Set up JDA audio handlers if we have custom providers
                AudioProvider provider = audioProviders.get(guildId);
                if (provider != null) {
                    audioManager.setSendingHandler(new JDAAudioSendHandler(provider));
                }

                AudioReceiver receiver = audioReceivers.get(guildId);
                if (receiver != null) {
                    audioManager.setReceivingHandler(new JDAAudioReceiveHandler(receiver));
                }

                // Connect to voice channel
                connectionStatuses.put(guildId, VoiceConnectionStatus.CONNECTING);
                audioManager.openAudioConnection(voiceChannel);

                logger.info("Connected to voice channel {} in guild {}", voiceChannel.getName(), guild.getName());
                connectionStatuses.put(guildId, VoiceConnectionStatus.CONNECTED);
                return VoiceConnectionStatus.CONNECTED;

            } catch (Exception e) {
                logger.error("Failed to connect to voice channel: {}", e.getMessage(), e);
                connectionStatuses.put(guildId, VoiceConnectionStatus.ERROR);
                return VoiceConnectionStatus.ERROR;
            }
        });
    }

    @Override
    public CompletableFuture<Void> disconnect(long guildId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Guild guild = jda.getGuildById(guildId);
                if (guild == null) {
                    return;
                }

                AudioManager audioManager = guild.getAudioManager();
                audioManager.closeAudioConnection();

                connectionStatuses.put(guildId, VoiceConnectionStatus.DISCONNECTED);

                // Clean up audio handlers
                AudioProvider provider = audioProviders.remove(guildId);
                if (provider != null) {
                    provider.close();
                }

                AudioReceiver receiver = audioReceivers.remove(guildId);
                if (receiver != null) {
                    receiver.close();
                }

                logger.info("Disconnected from voice in guild {}", guild.getName());
            } catch (Exception e) {
                logger.error("Error disconnecting from voice: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    public VoiceConnectionStatus getConnectionStatus(long guildId) {
        return connectionStatuses.getOrDefault(guildId, VoiceConnectionStatus.DISCONNECTED);
    }

    @Override
    public boolean isConnected(long guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return false;
        }
        return guild.getAudioManager().isConnected();
    }

    @Override
    public Long getConnectedChannelId(long guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return null;
        }
        var connectedChannel = guild.getAudioManager().getConnectedChannel();
        return connectedChannel != null ? connectedChannel.getIdLong() : null;
    }

    @Override
    public void setAudioProvider(long guildId, AudioProvider provider) {
        if (provider == null) {
            AudioProvider old = audioProviders.remove(guildId);
            if (old != null) {
                old.close();
            }
        } else {
            audioProviders.put(guildId, provider);
        }

        // Update JDA handler if connected
        Guild guild = jda.getGuildById(guildId);
        if (guild != null && guild.getAudioManager().isConnected()) {
            if (provider != null) {
                guild.getAudioManager().setSendingHandler(new JDAAudioSendHandler(provider));
            } else {
                guild.getAudioManager().setSendingHandler(null);
            }
        }
    }

    @Override
    public AudioProvider getAudioProvider(long guildId) {
        return audioProviders.get(guildId);
    }

    @Override
    public void setAudioReceiver(long guildId, AudioReceiver receiver) {
        if (receiver == null) {
            AudioReceiver old = audioReceivers.remove(guildId);
            if (old != null) {
                old.close();
            }
        } else {
            audioReceivers.put(guildId, receiver);
        }

        // Update JDA handler if connected
        Guild guild = jda.getGuildById(guildId);
        if (guild != null && guild.getAudioManager().isConnected()) {
            if (receiver != null) {
                guild.getAudioManager().setReceivingHandler(new JDAAudioReceiveHandler(receiver));
            } else {
                guild.getAudioManager().setReceivingHandler(null);
            }
        }
    }

    @Override
    public AudioReceiver getAudioReceiver(long guildId) {
        return audioReceivers.get(guildId);
    }

    @Override
    public boolean isDAVEAvailable(long guildId) {
        return activeDAVEProvider != null && activeDAVEProvider.isAvailable();
    }

    @Override
    public boolean isJavaVersionCompatible() {
        int javaVersion = Runtime.version().feature();
        // At minimum, we need Java 8 for libdave-jvm
        return javaVersion >= MINIMUM_JAVA_VERSION_FOR_LIBDAVE;
    }

    /**
     * Check if DAVE is currently required based on the deadline.
     */
    private boolean isDAVERequired() {
        return !LocalDate.now().isBefore(DAVE_DEADLINE);
    }

    @Override
    public void registerDAVEProvider(String pluginName, DAVEProvider provider) {
        if (pluginName == null || provider == null) {
            logger.warn("Attempted to register null DAVE provider");
            return;
        }

        int javaVersion = Runtime.version().feature();
        int requiredVersion = provider.getRequiredJavaVersion();

        if (javaVersion < requiredVersion) {
            logger.error("DAVE provider '{}' from plugin '{}' requires Java {} but running Java {}",
                provider.getName(), pluginName, requiredVersion, javaVersion);
            return;
        }

        daveProviders.put(pluginName, provider);
        logger.info("DAVE provider '{}' v{} registered by plugin '{}'",
            provider.getName(), provider.getVersion(), pluginName);

        // Set as active if none or if this one has higher priority
        if (activeDAVEProvider == null ||
            provider.getRequiredJavaVersion() > activeDAVEProvider.getRequiredJavaVersion()) {
            activeDAVEProvider = provider;
            logger.info("Active DAVE provider set to: {} v{}",
                provider.getName(), provider.getVersion());
        }
    }

    @Override
    public void unregisterDAVEProvider(String pluginName) {
        DAVEProvider removed = daveProviders.remove(pluginName);
        if (removed != null) {
            removed.shutdown();
            logger.info("DAVE provider from plugin '{}' unregistered", pluginName);

            // If we removed the active provider, find another
            if (removed == activeDAVEProvider) {
                activeDAVEProvider = daveProviders.values().stream()
                    .filter(DAVEProvider::isAvailable)
                    .findFirst()
                    .orElse(null);

                if (activeDAVEProvider != null) {
                    logger.info("Active DAVE provider switched to: {} v{}",
                        activeDAVEProvider.getName(), activeDAVEProvider.getVersion());
                } else {
                    logger.warn("No DAVE providers available. Voice connections will fail after deadline.");
                }
            }
        }
    }

    @Override
    public DAVEProvider getActiveDAVEProvider() {
        return activeDAVEProvider;
    }

    @Override
    public void setSelfMuted(long guildId, boolean muted) {
        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            guild.getAudioManager().setSelfMuted(muted);
        }
    }

    @Override
    public boolean isSelfMuted(long guildId) {
        Guild guild = jda.getGuildById(guildId);
        return guild != null && guild.getAudioManager().isSelfMuted();
    }

    @Override
    public void setSelfDeafened(long guildId, boolean deafened) {
        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            guild.getAudioManager().setSelfDeafened(deafened);
        }
    }

    @Override
    public boolean isSelfDeafened(long guildId) {
        Guild guild = jda.getGuildById(guildId);
        return guild != null && guild.getAudioManager().isSelfDeafened();
    }
}

