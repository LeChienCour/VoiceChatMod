package net.dsandov.voicechatmod.audio;

/**
 * Interface for an audio decoder.
 * A decoder takes encoded audio data and decompresses it back into raw PCM audio data.
 */
public interface IAudioDecoder {
    /**
     * Decodes a chunk of encoded audio data back into raw PCM audio.
     *
     * @param encodedData The encoded audio data.
     * @return The decoded raw PCM audio data as a byte array.
     * Returns null or an empty array if decoding fails or if encodedData is invalid.
     */
    byte[] decode(byte[] encodedData);

    /**
     * Gets the audio format of the PCM data produced by this decoder.
     * This is important for the audio playback system to know how to handle the data.
     * @return The output PCM AudioFormat.
     */
    // javax.sound.sampled.AudioFormat getOutputPCMFormat(); // Optional: if decoders produce a specific output format

    /**
     * Any cleanup or resource release needed by the decoder.
     */
    // void close(); // Optional
}