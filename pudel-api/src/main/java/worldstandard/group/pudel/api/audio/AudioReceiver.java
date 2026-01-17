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

/**
 * Interface for receiving audio data from Discord voice connections.
 * Plugins implementing audio recording or speech-to-text functionality
 * should implement this interface.
 *
 * <p>Note: Receiving audio from Discord requires the bot to have the
 * appropriate permissions and the connection to be DAVE-compliant.</p>
 */
public interface AudioReceiver {

    /**
     * Called when audio data is received from a user.
     *
     * @param userId the ID of the user speaking
     * @param audioData the received audio data (Opus-encoded)
     */
    void handleAudio(long userId, byte[] audioData);

    /**
     * Called when combined audio from all users is received.
     * This is useful for recording the entire voice channel.
     *
     * @param audioData the combined audio data
     */
    default void handleCombinedAudio(byte[] audioData) {
        // Default empty implementation - override if needed
    }

    /**
     * Called when a user starts speaking.
     *
     * @param userId the ID of the user who started speaking
     */
    default void handleUserStartSpeaking(long userId) {
        // Default empty implementation
    }

    /**
     * Called when a user stops speaking.
     *
     * @param userId the ID of the user who stopped speaking
     */
    default void handleUserStopSpeaking(long userId) {
        // Default empty implementation
    }

    /**
     * Check if this receiver wants Opus-encoded audio (true) or decoded PCM (false).
     *
     * @return true for Opus data, false for decoded PCM
     */
    default boolean wantsOpus() {
        return true;
    }

    /**
     * Called when the audio receiver is being closed.
     * Clean up any resources here.
     */
    default void close() {
        // Default empty implementation
    }
}

