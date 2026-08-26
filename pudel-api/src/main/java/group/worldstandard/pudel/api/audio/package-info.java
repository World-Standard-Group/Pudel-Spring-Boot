/**
 * Audio and voice connection management API.
 *
 * <p>This package provides interfaces for managing Discord voice connections,
 * audio playback, and audio recording. Plugins can use these interfaces to
 * implement music bots, voice recording, speech-to-text, and other audio features.</p>
 *
 * <p><b>DAVE Requirement:</b> Starting March 1st, 2026, all voice connections require
 * DAVE (Discord Audio/Voice Encryption). Plugins MUST provide a {@link group.worldstandard.pudel.api.audio.DAVEProvider}
 * when connecting to voice channels.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.audio.AudioProvider} - Interface for providing audio data to play</li>
 *   <li>{@link group.worldstandard.pudel.api.audio.AudioReceiver} - Interface for receiving audio data</li>
 *   <li>{@link group.worldstandard.pudel.api.audio.DAVEProvider} - Interface for DAVE encryption implementation</li>
 *   <li>{@link group.worldstandard.pudel.api.audio.VoiceConnectionStatus} - Enum of connection states</li>
 *   <li>{@link group.worldstandard.pudel.api.audio.VoiceManager} - Main interface for voice connection management</li>
 * </ul>
 *
 * <h2>Audio Playback Example:</h2>
 * <pre>{@code
 * VoiceManager voiceManager = context.getVoiceManager();
 *
 * // Connect to voice channel
 * voiceManager.connect(guildId, voiceChannelId);
 *
 * // Set audio provider
 * voiceManager.setAudioProvider(guildId, new MyAudioProvider());
 * }</pre>
 *
 * <h2>Audio Receiving Example:</h2>
 * <pre>{@code
 * // Set audio receiver for recording
 * voiceManager.setAudioReceiver(guildId, new AudioReceiver() {
 *     @Override
 *     public void handleAudio(long userId, byte[] audioData) {
 *         // Process received audio
 *     }
 * });
 * }</pre>
 *
 * @since 2.3.0
 */
package group.worldstandard.pudel.api.audio;