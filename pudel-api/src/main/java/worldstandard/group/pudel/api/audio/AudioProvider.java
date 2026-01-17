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
 * Interface for providing audio data to Discord voice connections.
 * Plugins implementing audio playback functionality should implement this interface.
 *
 * <p>Audio data must be in Opus format for Discord compatibility.</p>
 */
public interface AudioProvider {

    /**
     * Check if there is audio data available to send.
     * This is called before provide20MsAudio() to avoid unnecessary processing.
     *
     * @return true if audio data can be provided
     */
    boolean canProvide();

    /**
     * Provide 20ms of audio data.
     * This method is called roughly every 20ms when audio is being sent.
     *
     * <p>The returned byte array should contain Opus-encoded audio data.</p>
     *
     * @return Opus-encoded audio data, or null if no data is available
     */
    byte[] provide20MsAudio();

    /**
     * Check if the audio data is already Opus encoded.
     * If false, JDA will attempt to encode the audio.
     *
     * @return true if the data from provide20MsAudio() is Opus-encoded
     */
    default boolean isOpus() {
        return true;
    }

    /**
     * Called when the audio provider is being closed.
     * Clean up any resources here.
     */
    default void close() {
        // Default empty implementation
    }
}
