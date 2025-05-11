// File: src/main/java/net/dsandov/voicechatmod/audio/MicrophoneManager.java
package net.dsandov.voicechatmod.audio;

import net.dsandov.voicechatmod.VoiceChatMod; // For logging

import javax.sound.sampled.*;

public class MicrophoneManager {

    // The line from which audio data is read from the microphone
    private TargetDataLine targetDataLine;
    // The format of the audio data we want to capture
    private AudioFormat audioFormat;

    // Flag to indicate if audio capture is currently active
    private volatile boolean isCapturing = false; // volatile as it might be accessed by multiple threads

    // Thread for actually reading data from the microphone
    private Thread captureThread;

    /**
     * Constructor for MicrophoneManager.
     * Initializes the desired audio format.
     */
    public MicrophoneManager() {
        this.audioFormat = getDesiredAudioFormat();
    }

    /**
     * Defines the standard audio format we want to use for voice chat.
     * Common settings for voice: 16kHz sample rate, 16-bit sample size, mono, signed PCM.
     *
     * @return The desired AudioFormat.
     */
    private AudioFormat getDesiredAudioFormat() {
        // Sample rate: 16000 Hz (16 kHz) - good quality for voice
        float sampleRate = 16000.0F;
        // Sample size in bits: 16-bit - standard for good quality
        int sampleSizeInBits = 16;
        // Channels: 1 for mono, 2 for stereo. Mono is sufficient for voice chat.
        int channels = 1;
        // Signed: true for PCM data that ranges from negative to positive values.
        boolean signed = true;
        // Big-endian: false for little-endian, which is common for PCM WAVE audio.
        boolean bigEndian = false;

        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    /**
     * Initializes the microphone by trying to find and open a TargetDataLine.
     *
     * @return true if initialization was successful, false otherwise.
     */
    public boolean initialize() {
        if (this.targetDataLine != null && this.targetDataLine.isOpen()) {
            VoiceChatMod.LOGGER.info("MicrophoneManager already initialized and line is open.");
            return true;
        }

        try {
            // Get a TargetDataLine that matches the specified AudioFormat.
            // This will attempt to get the system's default microphone supporting the format.
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

            // Check if any microphone supports this format
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                VoiceChatMod.LOGGER.error("Audio format not supported by any available microphone line.");
                VoiceChatMod.LOGGER.error("Format details: {}", audioFormat.toString());
                // You might want to try other formats or allow user selection here in a more advanced version.
                return false;
            }

            this.targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            VoiceChatMod.LOGGER.info("TargetDataLine obtained for format: {}", audioFormat.toString());
            return true;

        } catch (LineUnavailableException e) {
            VoiceChatMod.LOGGER.error("Failed to get a TargetDataLine: Line unavailable.", e);
            this.targetDataLine = null; // Ensure it's null if failed
            return false;
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("An unexpected error occurred during microphone initialization.", e);
            this.targetDataLine = null;
            return false;
        }
    }


    /**
     * Starts capturing audio from the microphone.
     * This method will open the microphone line, start it, and begin reading data in a separate thread.
     */
    public void startCapture() {
        if (isCapturing) {
            VoiceChatMod.LOGGER.warn("Capture is already in progress.");
            return;
        }

        if (this.targetDataLine == null) {
            VoiceChatMod.LOGGER.error("TargetDataLine is not initialized. Cannot start capture.");
            VoiceChatMod.LOGGER.info("Attempting to re-initialize microphone...");
            if (!initialize()) { // Try to initialize if it wasn't
                VoiceChatMod.LOGGER.error("Re-initialization failed. Cannot start capture.");
                return;
            }
        }

        try {
            // Open the line with the specified audio format and a recommended buffer size.
            // If the line is already open, this call does nothing.
            if (!this.targetDataLine.isOpen()) {
                this.targetDataLine.open(audioFormat, this.targetDataLine.getBufferSize()); // Use a good buffer size
                VoiceChatMod.LOGGER.info("Microphone line opened.");
            }


            // Start the line. This allows data to begin flowing.
            this.targetDataLine.start();
            isCapturing = true;
            VoiceChatMod.LOGGER.info("Microphone capture started.");

            // Create and start the thread that will read audio data from the line.
            captureThread = new Thread(this::captureAudioDataLoop, "VoiceChat-CaptureThread");
            captureThread.setDaemon(true); // Make it a daemon thread so it doesn't prevent JVM shutdown
            captureThread.start();

        } catch (LineUnavailableException e) {
            VoiceChatMod.LOGGER.error("Failed to open or start microphone line: Line unavailable.", e);
            isCapturing = false; // Ensure this is false if we failed
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("An unexpected error occurred while starting capture.", e);
            isCapturing = false;
        }
    }

    /**
     * The loop that runs in a separate thread to continuously read audio data.
     */
    private void captureAudioDataLoop() {
        // Buffer to hold chunks of audio data read from the line.
        // A common buffer size might be related to how often you want to send packets.
        // For 16kHz, 16-bit mono, 20ms of audio = 16000 * (16/8) * 0.020 = 640 bytes.
        byte[] buffer = new byte[640]; // Example: 20ms of audio data at 16kHz, 16-bit mono
        int bytesRead;

        VoiceChatMod.LOGGER.info("Audio capture loop started on thread: {}", Thread.currentThread().getName());

        while (isCapturing) {
            if (targetDataLine == null || !targetDataLine.isOpen()) {
                VoiceChatMod.LOGGER.error("TargetDataLine became null or closed during capture. Stopping loop.");
                isCapturing = false; // Ensure the loop terminates
                break;
            }
            // Read data from the TargetDataLine into the buffer.
            // This call will block until data is available.
            bytesRead = targetDataLine.read(buffer, 0, buffer.length);

            if (bytesRead > 0) {
                // TODO: Process the captured audio data (buffer)
                // For now, we just log that we read some data.
                // In the next steps, this data would be:
                // 1. (Optionally) Encoded using an audio codec.
                // 2. Sent over the network (via AppSync).
                VoiceChatMod.LOGGER.debug("Read {} bytes from microphone.", bytesRead);

                // Create a copy of the buffer if passing it to another thread or queue
                // byte[] audioDataPacket = Arrays.copyOf(buffer, bytesRead);
                // Send 'audioDataPacket' to the network module.
            } else if (bytesRead == -1) {
                // End of stream, which shouldn't happen for a microphone unless it's closed.
                VoiceChatMod.LOGGER.warn("Microphone line reported end of stream.");
                isCapturing = false; // Stop capturing
                break;
            }
        }
        VoiceChatMod.LOGGER.info("Audio capture loop finished.");
    }


    /**
     * Stops capturing audio from the microphone.
     * This method will stop and close the microphone line.
     */
    public void stopCapture() {
        if (!isCapturing) {
            // VoiceChatMod.LOGGER.warn("Capture is not currently in progress.");
            return;
        }

        isCapturing = false; // Signal the capture thread to stop

        if (captureThread != null) {
            try {
                // Give the capture thread a moment to finish its current read and exit gracefully
                captureThread.join(100); // Wait up to 100ms
            } catch (InterruptedException e) {
                VoiceChatMod.LOGGER.warn("Interrupted while waiting for capture thread to finish.", e);
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
        }
        captureThread = null;


        if (targetDataLine != null) {
            try {
                // Check if it's running or open before trying to stop/close
                if (targetDataLine.isRunning()) {
                    targetDataLine.stop(); // Stop capturing data
                    VoiceChatMod.LOGGER.info("Microphone line stopped.");
                }
                if (targetDataLine.isOpen()) {
                    targetDataLine.flush(); // Discard any unread data
                    targetDataLine.close(); // Release the system resource
                    VoiceChatMod.LOGGER.info("Microphone line closed.");
                }
            } catch (Exception e) {
                // Catching general exception as some implementations might throw SecurityException
                // or others if the line state is unexpected.
                VoiceChatMod.LOGGER.error("Error while stopping or closing microphone line.", e);
            }
        } else {
            VoiceChatMod.LOGGER.warn("TargetDataLine was null, cannot stop/close.");
        }

        VoiceChatMod.LOGGER.info("Microphone capture stopped.");
    }

    /**
     * Checks if audio is currently being captured.
     *
     * @return true if capturing, false otherwise.
     */
    public boolean isCapturing() {
        return isCapturing && targetDataLine != null && targetDataLine.isOpen() && targetDataLine.isRunning();
    }

    // --- Helper methods for listing available microphones (for future use or debugging) ---

    /**
     * Lists all available audio mixers and their TargetDataLine support for the desired format.
     * Useful for debugging or allowing users to select a microphone.
     */
    public static void listAvailableMicrophones() {
        VoiceChatMod.LOGGER.info("--- Listing Available Audio Input Mixers ---");
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        AudioFormat desiredFormat = new MicrophoneManager().getDesiredAudioFormat(); // Get a sample format

        for (Mixer.Info mixerInfo : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] targetLineInfos = mixer.getTargetLineInfo(new DataLine.Info(TargetDataLine.class, desiredFormat));

            if (targetLineInfos.length > 0) {
                VoiceChatMod.LOGGER.info("Mixer: {} (Supports TargetDataLine for format)", mixerInfo.getName());
                VoiceChatMod.LOGGER.debug("  Description: {}", mixerInfo.getDescription());
                VoiceChatMod.LOGGER.debug("  Vendor: {}", mixerInfo.getVendor());
                VoiceChatMod.LOGGER.debug("  Version: {}", mixerInfo.getVersion());
            } else {
                // Log all mixers even if they don't support the line, for general info
                // VoiceChatMod.LOGGER.debug("Mixer: {} (Does NOT support TargetDataLine for format or no line available)", mixerInfo.getName());
            }
        }
        VoiceChatMod.LOGGER.info("--- End of Audio Input Mixer List ---");
    }
}