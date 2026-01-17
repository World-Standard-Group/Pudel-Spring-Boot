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

import net.dv8tion.jda.api.audio.AudioSendHandler;
import worldstandard.group.pudel.api.audio.AudioProvider;

import java.nio.ByteBuffer;

/**
 * Adapter that wraps a plugin's AudioProvider for JDA's AudioSendHandler interface.
 */
public class JDAAudioSendHandler implements AudioSendHandler {

    private final AudioProvider provider;
    private ByteBuffer lastFrame;

    public JDAAudioSendHandler(AudioProvider provider) {
        this.provider = provider;
    }

    @Override
    public boolean canProvide() {
        return provider.canProvide();
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        byte[] audioData = provider.provide20MsAudio();
        if (audioData == null) {
            return null;
        }
        lastFrame = ByteBuffer.wrap(audioData);
        return lastFrame;
    }

    @Override
    public boolean isOpus() {
        return provider.isOpus();
    }
}
