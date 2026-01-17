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
 * Represents the status of a voice connection.
 */
public enum VoiceConnectionStatus {
    /**
     * Not connected to any voice channel.
     */
    DISCONNECTED,

    /**
     * Currently connecting to a voice channel.
     */
    CONNECTING,

    /**
     * Connected to a voice channel and ready.
     */
    CONNECTED,

    /**
     * Connected but audio is not being sent/received.
     */
    CONNECTED_NO_AUDIO,

    /**
     * Connection failed due to missing DAVE implementation.
     */
    DAVE_REQUIRED,

    /**
     * Connection failed due to DAVE initialization error.
     */
    DAVE_ERROR,

    /**
     * Connection failed due to permissions.
     */
    NO_PERMISSION,

    /**
     * Already connected to a voice channel.
     */
    ALREADY_CONNECTED,

    /**
     * Connection failed due to unknown error.
     */
    ERROR
}

