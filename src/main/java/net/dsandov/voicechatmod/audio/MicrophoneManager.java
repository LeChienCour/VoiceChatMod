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
    // Audio Encoder
    private IAudioEncoder audioEncoder;

    // --- START OF EASILY REMOVABLE/MODIFIABLE MODULE FOR MICROPHONE SELECTION ---
    /**
     * Temporary constant to specify a preferred microphone for testing.
     * To revert to default microphone selection behavior, set this to "" (empty string) or null.
     * This makes it easy to find and change/remove this specific microphone preference later.
     */
    private static final String HARDCODED_TEST_MICROPHONE_NAME = "Microphone (HD Pro Webcam C920)";
    // --- END OF EASILY REMOVABLE/MODIFIABLE MODULE FOR MICROPHONE SELECTION ---

    /**
     * Constructor for MicrophoneManager.
     * Initializes the desired audio format.
     */
    public MicrophoneManager() {
        this.audioFormat = getDesiredAudioFormat();
        // Initialize the encoder
        this.audioEncoder = new PassthroughEncoder();
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
     * It will first attempt to use the HARDCODED_TEST_MICROPHONE_NAME if specified.
     *
     * @return true if initialization was successful, false otherwise.
     */
    public boolean initialize() {
        if (this.targetDataLine != null && this.targetDataLine.isOpen()) {
            VoiceChatMod.LOGGER.info("MicrophoneManager already initialized and line is open.");
            return true;
        }
        // Use the centrally defined preferred microphone name
        final String effectivePreferredMicName = HARDCODED_TEST_MICROPHONE_NAME;
        try {
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            Mixer.Info selectedMixerInfo = null;
            if (effectivePreferredMicName != null && !effectivePreferredMicName.isEmpty()) {
                VoiceChatMod.LOGGER.info("Attempting to find hardcoded test microphone: '{}'", effectivePreferredMicName);
                boolean foundPreferred = false;
                for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                    if (mixerInfo.getName().equals(effectivePreferredMicName)) {
                        Mixer mixer = AudioSystem.getMixer(mixerInfo);
                        if (mixer.isLineSupported(dataLineInfo)) { // Check if this specific mixer supports the line and format
                            selectedMixerInfo = mixerInfo;
                            VoiceChatMod.LOGGER.info("Found preferred microphone and it supports the required format: {}", mixerInfo.getName());
                            foundPreferred = true;
                            break;
                        } else {
                            VoiceChatMod.LOGGER.warn("Preferred microphone '{}' found, but it does NOT support the required audio format ({}).", effectivePreferredMicName, audioFormat.toString());
                        }
                    }
                }
                if (!foundPreferred && selectedMixerInfo == null) { // Check if preferred was specified but not found or not suitable
                    VoiceChatMod.LOGGER.warn("Hardcoded preferred microphone '{}' was not found or is not suitable. Will attempt default.", effectivePreferredMicName);
                }
            } else {
                VoiceChatMod.LOGGER.info("No hardcoded test microphone name set. Will attempt default system microphone line.");
            }


            // Get the TargetDataLine
            if (selectedMixerInfo != null) {
                // Get line from the specifically selected mixer
                Mixer mixer = AudioSystem.getMixer(selectedMixerInfo);
                this.targetDataLine = (TargetDataLine) mixer.getLine(dataLineInfo);
                VoiceChatMod.LOGGER.info("Using specific microphone: {}", selectedMixerInfo.getName());
            } else {
                // Fallback to default system line if no preferred mic is set, found, or suitable
                VoiceChatMod.LOGGER.info("Attempting to get default system microphone line (fallback).");
                if (!AudioSystem.isLineSupported(dataLineInfo)) {
                    VoiceChatMod.LOGGER.error("Audio format not supported by any available microphone line (default path).");
                    VoiceChatMod.LOGGER.error("Format details: {}", audioFormat.toString());
                    return false;
                }
                this.targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
                VoiceChatMod.LOGGER.info("Using default system microphone line.");
            }

            VoiceChatMod.LOGGER.info("TargetDataLine obtained. Line Info: {}. Audio Format: {}", this.targetDataLine.getLineInfo().toString(), audioFormat.toString());
            // The line is obtained but not yet open. It will be opened in startCapture().
            return true;

        } catch (LineUnavailableException e) {
            VoiceChatMod.LOGGER.error("Failed to get a TargetDataLine: Line unavailable. This can happen if another app is using the mic, it's disabled, or permissions are missing.", e);
            this.targetDataLine = null;
            return false;
        } catch (IllegalArgumentException e) {
            VoiceChatMod.LOGGER.error("Failed to get a TargetDataLine: Audio format parameters are not supported or line cannot be created with them.", e);
            this.targetDataLine = null;
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
                VoiceChatMod.LOGGER.debug("Read {} raw bytes from microphone.", bytesRead);

                // Create a precise copy of the data that was actually read
                byte[] rawPcmData = new byte[bytesRead];
                System.arraycopy(buffer, 0, rawPcmData, 0, bytesRead);

                // Encode the raw PCM data
                byte[] encodedData = this.audioEncoder.encode(rawPcmData); // Use the encoder

                if (encodedData != null && encodedData.length > 0) {
                    VoiceChatMod.LOGGER.debug("Encoded audio data: {} bytes.", encodedData.length);
                    // Pass the encoded data to the loopback buffer (or network layer later)
                    VoiceChatMod.ClientModEvents.appendToLoopbackBuffer(encodedData, encodedData.length);
                } else {
                    VoiceChatMod.LOGGER.warn("Encoding produced null or empty data.");
                }

            } else if (bytesRead == -1) {
                VoiceChatMod.LOGGER.warn("Microphone line reported end of stream.");
                isCapturing = false;
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