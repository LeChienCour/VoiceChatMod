package net.dsandov.voicechatmod.audio;

/**
 * Interface for an audio encoder.
 * An encoder takes raw PCM audio data and compresses it into an encoded format.
 */
public interface IAudioEncoder {
    /**
     * Encodes a chunk of raw PCM audio data.
     *
     * @param pcmData The raw Pulse Code Modulation (PCM) audio data.
     * @return The encoded audio data as a byte array.
     * Returns null or an empty array if encoding fails or if pcmData is invalid.
     */
    byte[] encode(byte[] pcmData);

    /**
     * Gets the audio format that this encoder expects for its input PCM data.
     * @return The input AudioFormat.
     */
    // javax.sound.sampled.AudioFormat getExpectedInputFormat(); // Optional: if encoders are tied to specific input formats

    /**
     * Gets the audio format of the data produced by this encoder.
     * For some codecs, this might be different or irrelevant (just a byte stream).
     * @return The output AudioFormat or null if not applicable.
     */
    // javax.sound.sampled.AudioFormat getEncodedOutputFormat(); // Optional

    /**
     * Any cleanup or resource release needed by the encoder.
     */
    // void close(); // Optional: if encoder needs explicit cleanup
}