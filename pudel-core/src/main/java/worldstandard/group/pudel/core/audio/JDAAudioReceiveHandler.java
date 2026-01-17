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

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.audio.UserAudio;
import worldstandard.group.pudel.api.audio.AudioReceiver;

/**
 * Adapter that wraps a plugin's AudioReceiver for JDA's AudioReceiveHandler interface.
 */
public class JDAAudioReceiveHandler implements AudioReceiveHandler {

    private final AudioReceiver receiver;

    public JDAAudioReceiveHandler(AudioReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public boolean canReceiveCombined() {
        return true;
    }

    @Override
    public boolean canReceiveUser() {
        return true;
    }

    @Override
    public void handleCombinedAudio(CombinedAudio combinedAudio) {
        byte[] audioData;
        if (receiver.wantsOpus()) {
            // Get raw Opus data - not directly available from CombinedAudio
            // Fall back to PCM and let the receiver handle it
            audioData = combinedAudio.getAudioData(1.0);
        } else {
            audioData = combinedAudio.getAudioData(1.0);
        }
        receiver.handleCombinedAudio(audioData);
    }

    @Override
    public void handleUserAudio(UserAudio userAudio) {
        long userId = userAudio.getUser().getIdLong();
        byte[] audioData;
        if (receiver.wantsOpus()) {
            // Get Opus data if available
            audioData = userAudio.getAudioData(1.0);
        } else {
            audioData = userAudio.getAudioData(1.0);
        }
        receiver.handleAudio(userId, audioData);
    }

    @Override
    public boolean includeUserInCombinedAudio(net.dv8tion.jda.api.entities.User user) {
        return true;
    }
}

