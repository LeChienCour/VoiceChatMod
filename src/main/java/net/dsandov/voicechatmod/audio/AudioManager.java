package net.dsandov.voicechatmod.audio;

import net.dsandov.voicechatmod.VoiceChatMod; // For logging

import javax.sound.sampled.*;

public class AudioManager {

    // The line to which audio data is written for playback
    private SourceDataLine sourceDataLine;
    // The format of the audio data we expect to play
    private AudioFormat audioFormat; // This should match the capture format
    // Flag to indicate if audio playback is currently possible/initialized
    private boolean isInitialized = false;
    // Add a field for the audio decoder
    private IAudioDecoder audioDecoder;

    /**
     * Constructor for AudioManager.
     * It's good practice to pass the expected audio format or have a method to set it.
     */
    public AudioManager() {
        // Using a default format initially, can be made more flexible
        this.audioFormat = getPlaybackAudioFormat();
        // Initialize the decoder
        this.audioDecoder = new PassthroughDecoder();
    }

    /**
     * Defines the standard audio format for playback.
     * This typically matches the capture format.
     * @return The desired AudioFormat for playback.
     */
    private AudioFormat getPlaybackAudioFormat() {
        // These should ideally be consistent with MicrophoneManager's format
        float sampleRate = 16000.0F;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    /**
     * Initializes the audio output system by trying to find and open a SourceDataLine.
     *
     * @return true if initialization was successful, false otherwise.
     */
    public boolean initialize() {
        if (this.sourceDataLine != null && this.sourceDataLine.isOpen()) {
            VoiceChatMod.LOGGER.info("AudioManager already initialized and line is open.");
            return true;
        }

        try {
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                VoiceChatMod.LOGGER.error("Audio format not supported by any available speaker line for playback.");
                VoiceChatMod.LOGGER.error("Playback Format details: {}", audioFormat.toString());
                return false;
            }

            this.sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            this.sourceDataLine.open(audioFormat); // Open the line with the specified format
            this.sourceDataLine.start(); // Start the line so it's ready to receive data for playback

            VoiceChatMod.LOGGER.info("SourceDataLine obtained and started for playback. Format: {}", audioFormat.toString());
            isInitialized = true;
            return true;

        } catch (LineUnavailableException e) {
            VoiceChatMod.LOGGER.error("Failed to get or open a SourceDataLine for playback: Line unavailable.", e);
            this.sourceDataLine = null;
            isInitialized = false;
            return false;
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("An unexpected error occurred during audio output initialization.", e);
            this.sourceDataLine = null;
            isInitialized = false;
            return false;
        }
    }

    /**
     * Plays the given audio data.
     *
     * @param audioData The byte array containing the audio data to play.
     * @param offset The starting offset in the byte array.
     * @param length The number of bytes to play from the array.
     */
    public void playAudio(byte[] audioData, int offset, int length) {
        if (!isInitialized || this.sourceDataLine == null || !this.sourceDataLine.isOpen()) {
            VoiceChatMod.LOGGER.warn("AudioManager not initialized or line closed. Cannot play audio. isInitialized: {}, line null: {}, line open: {}",
                    isInitialized, this.sourceDataLine == null, (this.sourceDataLine != null && this.sourceDataLine.isOpen()));
            return;
        }

        if (audioData == null || length <= 0) {
            VoiceChatMod.LOGGER.warn("Audio data is null or length is zero. Nothing to play.");
            return;
        }

        try {
            // Write directly to the source line for local playback
            int bytesWritten = sourceDataLine.write(audioData, offset, length);
            VoiceChatMod.LOGGER.debug("Wrote {} bytes to audio output", bytesWritten);
            
            if (bytesWritten != length) {
                VoiceChatMod.LOGGER.warn("Not all bytes were written to audio output! Expected: {}, Actual: {}", length, bytesWritten);
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error during audio playback", e);
        }
    }

    /**
     * Stops playback and closes the audio line.
     * Call this when the audio manager is no longer needed or the game is closing.
     */
    public void shutdown() {
        if (sourceDataLine != null) {
            VoiceChatMod.LOGGER.info("Shutting down AudioManager.");
            // Drain any buffered data to ensure everything is played before closing.
            // This blocks until all data has been played.
            if (sourceDataLine.isOpen()) {
                sourceDataLine.drain(); // Wait for buffer to empty
            }

            // Stop the line if it's running (it should be if we called start() in initialize)
            if (sourceDataLine.isRunning()) {
                sourceDataLine.stop();
            }

            // Close the line to release system resources
            if (sourceDataLine.isOpen()) {
                sourceDataLine.close();
            }
            VoiceChatMod.LOGGER.info("SourceDataLine closed.");
        }
        isInitialized = false;
        sourceDataLine = null;
    }

    /**
     * Gets the audio format used for playback.
     * @return The AudioFormat.
     */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    /**
     * Checks if the AudioManager is initialized and ready for playback.
     * @return true if initialized, false otherwise.
     */
    public boolean isInitialized() {
        return isInitialized;
    }
}