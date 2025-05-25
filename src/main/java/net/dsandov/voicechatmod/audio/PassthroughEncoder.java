package net.dsandov.voicechatmod.audio;

import net.dsandov.voicechatmod.VoiceChatMod; // For logging

/**
 * A simple "passthrough" implementation of IAudioEncoder.
 * This encoder does not perform any actual encoding/compression;
 * it simply returns the input PCM data unchanged.
 * Useful for testing the audio pipeline before a real codec is integrated.
 */
public class PassthroughEncoder implements IAudioEncoder {

    public PassthroughEncoder() {
        VoiceChatMod.LOGGER.info("PassthroughEncoder initialized. Audio data will not be compressed.");
    }

    @Override
    public byte[] encode(byte[] pcmData) {
        if (pcmData == null) {
            return null;
        }
        // No actual encoding, just return the data as is.
        // In a real encoder, this is where compression would happen.
        return pcmData;
    }
}