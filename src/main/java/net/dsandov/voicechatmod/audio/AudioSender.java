package net.dsandov.voicechatmod.audio;

/**
 * Interface for components that can send audio data.
 * Implemented by classes that need to send audio data to remote endpoints.
 */
public interface AudioSender {
    /**
     * Sends audio data to a remote endpoint.
     * 
     * @param base64Audio The audio data encoded in base64 format
     * @param author The name of the player who recorded the audio
     * @param timestamp The timestamp when the audio was recorded
     * @param format The audio format (e.g., "pcm")
     * @param encoding The encoding used (e.g., "base64")
     */
    void sendAudioData(String base64Audio, String author, String timestamp, String format, String encoding);
} 