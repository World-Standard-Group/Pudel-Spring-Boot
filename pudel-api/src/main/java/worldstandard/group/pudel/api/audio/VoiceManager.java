/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 Napapon Kamanee
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package worldstandard.group.pudel.api.audio;

import java.util.concurrent.CompletableFuture;

/**
 * Manager for voice connections and audio handling.
 *
 * <p>Plugins can use this interface to join/leave voice channels and manage audio.</p>
 *
 * <p><b>DAVE Requirement:</b> Starting March 1st, 2026, all voice connections require
 * DAVE (Discord Audio/Voice Encryption). Plugins MUST provide a {@link DAVEProvider}
 * when connecting to voice channels.</p>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * VoiceManager voiceManager = context.getVoiceManager();
 *
 * // Check DAVE availability before connecting
 * if (!voiceManager.isDAVEAvailable(guildId)) {
 *     context.log("error", "DAVE implementation required for voice");
 *     return;
 * }
 *
 * // Connect to voice channel
 * voiceManager.connect(guildId, voiceChannelId)
 *     .thenAccept(status -> {
 *         if (status == VoiceConnectionStatus.CONNECTED) {
 *             context.log("info", "Connected to voice!");
 *         }
 *     });
 * }</pre>
 */
public interface VoiceManager {

    /**
     * Connect to a voice channel.
     *
     * <p>This requires a DAVE implementation to be registered for the guild.</p>
     *
     * @param guildId the guild ID
     * @param voiceChannelId the voice channel ID
     * @return a future containing the connection status
     */
    CompletableFuture<VoiceConnectionStatus> connect(long guildId, long voiceChannelId);

    /**
     * Disconnect from a voice channel in a guild.
     *
     * @param guildId the guild ID
     * @return a future that completes when disconnected
     */
    CompletableFuture<Void> disconnect(long guildId);

    /**
     * Get the current connection status for a guild.
     *
     * @param guildId the guild ID
     * @return the current connection status
     */
    VoiceConnectionStatus getConnectionStatus(long guildId);

    /**
     * Check if the bot is connected to a voice channel in a guild.
     *
     * @param guildId the guild ID
     * @return true if connected
     */
    boolean isConnected(long guildId);

    /**
     * Get the voice channel ID the bot is connected to in a guild.
     *
     * @param guildId the guild ID
     * @return the voice channel ID, or null if not connected
     */
    Long getConnectedChannelId(long guildId);

    // ============== Audio Handling ==============

    /**
     * Set the audio provider for a guild.
     * The provider will supply audio data to be played in the voice channel.
     *
     * @param guildId the guild ID
     * @param provider the audio provider, or null to clear
     */
    void setAudioProvider(long guildId, AudioProvider provider);

    /**
     * Get the current audio provider for a guild.
     *
     * @param guildId the guild ID
     * @return the audio provider, or null if none set
     */
    AudioProvider getAudioProvider(long guildId);

    /**
     * Set the audio receiver for a guild.
     * The receiver will receive audio data from users in the voice channel.
     *
     * @param guildId the guild ID
     * @param receiver the audio receiver, or null to clear
     */
    void setAudioReceiver(long guildId, AudioReceiver receiver);

    /**
     * Get the current audio receiver for a guild.
     *
     * @param guildId the guild ID
     * @return the audio receiver, or null if none set
     */
    AudioReceiver getAudioReceiver(long guildId);

    // ============== DAVE Support ==============

    /**
     * Check if a DAVE implementation is available for use.
     *
     * <p>This checks if any DAVE provider has been registered and is ready.</p>
     *
     * @param guildId the guild ID (for future per-guild DAVE support)
     * @return true if DAVE is available
     */
    boolean isDAVEAvailable(long guildId);

    /**
     * Check if the current Java version is compatible with DAVE.
     *
     * @return true if Java version meets minimum requirements
     */
    boolean isJavaVersionCompatible();

    /**
     * Get the DAVE deadline date.
     *
     * @return the deadline date string "March 1st, 2026"
     */
    default String getDAVEDeadline() {
        return "March 1st, 2026";
    }

    /**
     * Register a DAVE provider for a plugin.
     * Plugins providing DAVE implementations should call this.
     *
     * @param pluginName the name of the plugin providing DAVE
     * @param provider the DAVE provider implementation
     */
    void registerDAVEProvider(String pluginName, DAVEProvider provider);

    /**
     * Unregister a DAVE provider.
     *
     * @param pluginName the name of the plugin
     */
    void unregisterDAVEProvider(String pluginName);

    /**
     * Get the currently active DAVE provider.
     *
     * @return the active DAVE provider, or null if none
     */
    DAVEProvider getActiveDAVEProvider();

    // ============== Self Mute/Deafen ==============

    /**
     * Set whether the bot is self-muted in a guild.
     *
     * @param guildId the guild ID
     * @param muted true to mute
     */
    void setSelfMuted(long guildId, boolean muted);

    /**
     * Check if the bot is self-muted in a guild.
     *
     * @param guildId the guild ID
     * @return true if self-muted
     */
    boolean isSelfMuted(long guildId);

    /**
     * Set whether the bot is self-deafened in a guild.
     *
     * @param guildId the guild ID
     * @param deafened true to deafen
     */
    void setSelfDeafened(long guildId, boolean deafened);

    /**
     * Check if the bot is self-deafened in a guild.
     *
     * @param guildId the guild ID
     * @return true if self-deafened
     */
    boolean isSelfDeafened(long guildId);
}

