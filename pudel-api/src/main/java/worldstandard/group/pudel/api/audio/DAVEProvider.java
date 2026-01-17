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
 * Interface for DAVE (Discord Audio/Voice Encryption) protocol implementation.
 *
 * <p><b>IMPORTANT:</b> Starting March 1st, 2026, Discord requires all voice connections
 * to use End-to-End Encryption (E2EE) via the DAVE protocol. Plugins that want to
 * use voice functionality MUST provide a DAVE implementation.</p>
 *
 * <p>Available implementations include:</p>
 * <ul>
 *   <li><a href="https://github.com/discord/jdave">JDAVE</a> - Requires Java 25+</li>
 *   <li><a href="https://github.com/KyokoBot/libdave-jvm">libdave-jvm</a> - Requires Java 8+</li>
 * </ul>
 *
 * <p>Plugin developers are responsible for choosing and bundling their DAVE implementation.</p>
 *
 * @see <a href="https://discord.com/developers/docs/topics/voice-connections#dave-protocol">Discord DAVE Protocol Documentation</a>
 */
public interface DAVEProvider {

    /**
     * Get the name of this DAVE implementation.
     *
     * @return the implementation name (e.g., "JDAVE", "libdave-jvm")
     */
    String getName();

    /**
     * Get the version of this DAVE implementation.
     *
     * @return the version string
     */
    String getVersion();

    /**
     * Check if this DAVE implementation is available and properly initialized.
     *
     * @return true if the implementation is ready to use
     */
    boolean isAvailable();

    /**
     * Get the minimum Java version required by this implementation.
     *
     * @return the minimum Java version (e.g., 8, 17, 25)
     */
    int getRequiredJavaVersion();

    /**
     * Initialize the DAVE implementation.
     * Called when the voice connection is being established.
     *
     * @throws DAVEException if initialization fails
     */
    void initialize() throws DAVEException;

    /**
     * Clean up resources when the voice connection is closed.
     */
    void shutdown();

    /**
     * Get the native library instance for JDA's VoiceEncryption interface.
     * This should return an object compatible with JDA's DAVE requirements.
     *
     * @return the native DAVE implementation object
     */
    Object getNativeImplementation();

    /**
     * Exception thrown when DAVE operations fail.
     */
    class DAVEException extends Exception {
        public DAVEException(String message) {
            super(message);
        }

        public DAVEException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

