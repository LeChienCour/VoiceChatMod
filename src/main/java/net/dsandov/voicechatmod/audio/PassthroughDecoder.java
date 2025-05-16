package net.dsandov.voicechatmod.audio;

import net.dsandov.voicechatmod.VoiceChatMod; // For logging

/**
 * A simple "passthrough" implementation of IAudioDecoder.
 * This decoder does not perform any actual decoding/decompression;
 * it simply returns the input encoded data unchanged, assuming it's already PCM.
 * Useful for testing the audio pipeline before a real codec is integrated.
 */
public class PassthroughDecoder implements IAudioDecoder {

    public PassthroughDecoder() {
        VoiceChatMod.LOGGER.info("PassthroughDecoder initialized. Audio data will not be decompressed (assumed PCM).");
    }

    @Override
    public byte[] decode(byte[] encodedData) {
        if (encodedData == null) {
            return null;
        }
        // No actual decoding, just return the data as is.
        // In a real decoder, this is where decompression would happen.
        return encodedData;
    }
}