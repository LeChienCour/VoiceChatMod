package net.dsandov.voicechatmod.audio;

import net.dsandov.voicechatmod.VoiceChatMod; // For logging
import net.minecraft.client.Minecraft;
import net.dsandov.voicechatmod.aws.VoiceGatewayClient;

import javax.sound.sampled.*;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages microphone capture and audio streaming.
 */
public class MicrophoneManager {
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
    
    private static final int BUFFER_SIZE = 4096;
    private static final int QUEUE_SIZE = 10;
    private static final long QUEUE_OFFER_TIMEOUT_MS = 100;
    
    private TargetDataLine microphoneLine;
    private Thread captureThread;
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private AudioSender audioSender;
    private String currentPlayerName = "Unknown";
    private final ArrayBlockingQueue<byte[]> audioQueue;
    private Thread processingThread;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

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
        audioQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
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
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            
            if (!AudioSystem.isLineSupported(info)) {
                VoiceChatMod.LOGGER.error("Microphone line not supported");
                return false;
            }

            microphoneLine = (TargetDataLine) AudioSystem.getLine(info);
            microphoneLine.open(AUDIO_FORMAT);
            VoiceChatMod.LOGGER.info("Microphone line opened successfully.");
            return true;
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to initialize microphone", e);
            return false;
        }
    }

    /**
     * Starts capturing audio from the microphone.
     * This method will open the microphone line, start it, and begin reading data in a separate thread.
     */
    public void startCapture() {
        if (microphoneLine == null || isCapturing.get()) {
            return;
        }

        try {
            microphoneLine.start();
            isCapturing.set(true);
            isProcessing.set(true);

            // Start capture thread
            captureThread = new Thread(this::captureLoop, "VoiceChat-CaptureThread");
            captureThread.start();

            // Start processing thread
            processingThread = new Thread(this::processLoop, "VoiceChat-ProcessingThread");
            processingThread.start();

            VoiceChatMod.LOGGER.info("Audio capture and processing threads started.");
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to start audio capture", e);
            stopCapture();
        }
    }

    private void captureLoop() {
        VoiceChatMod.LOGGER.info("Audio capture loop started on thread: {}", 
            Thread.currentThread().getName());

        byte[] buffer = new byte[BUFFER_SIZE];
        while (isCapturing.get()) {
            try {
                int count = microphoneLine.read(buffer, 0, buffer.length);
                if (count > 0) {
                    byte[] audioData = new byte[count];
                    System.arraycopy(buffer, 0, audioData, 0, count);
                    
                    if (!audioQueue.offer(audioData, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        VoiceChatMod.LOGGER.warn("Audio queue full, dropping packet");
                    }
                }
            } catch (Exception e) {
                if (isCapturing.get()) {
                    VoiceChatMod.LOGGER.error("Error during audio capture", e);
                }
            }
        }
    }

    private void processLoop() {
        while (isProcessing.get()) {
            try {
                byte[] audioData = audioQueue.poll(100, TimeUnit.MILLISECONDS);
                if (audioData != null && audioSender != null) {
                    String base64Audio = Base64.getEncoder().encodeToString(audioData);
                    String timestamp = Instant.now().toString();
                    // Get current player name before sending
                    currentPlayerName = VoiceChatMod.getCurrentPlayerName();
                    audioSender.sendAudioData(base64Audio, currentPlayerName, timestamp, "pcm", "base64");
                    VoiceChatMod.LOGGER.debug("Processed and sent {} bytes of audio data", audioData.length);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (isProcessing.get()) {
                    VoiceChatMod.LOGGER.error("Error processing audio data", e);
                }
            }
        }
    }

    /**
     * Stops capturing audio from the microphone.
     * This method will stop and close the microphone line.
     */
    public void stopCapture() {
        isCapturing.set(false);
        isProcessing.set(false);
        
        if (microphoneLine != null) {
            microphoneLine.stop();
            microphoneLine.flush();
        }
        
        // Clear the audio queue
        audioQueue.clear();
        
        // Wait for threads to finish
        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }
        
        if (processingThread != null) {
            try {
                processingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            processingThread = null;
        }
    }

    /**
     * Checks if audio is currently being captured.
     *
     * @return true if capturing, false otherwise.
     */
    public boolean isCapturing() {
        return isCapturing.get();
    }

    // --- Helper methods for listing available microphones (for future use or debugging) ---

    /**
     * Lists all available audio mixers and their TargetDataLine support for the desired format.
     * Useful for debugging or allowing users to select a microphone.
     */
    public static void listAvailableMicrophones() {
        VoiceChatMod.LOGGER.info("--- Listing Available Audio Input Mixers ---");
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        
        for (Mixer.Info mixerInfo : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] targetLineInfos = mixer.getTargetLineInfo(new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT));

            if (targetLineInfos.length > 0) {
                VoiceChatMod.LOGGER.info("Mixer: {} (Supports TargetDataLine for format)", mixerInfo.getName());
                VoiceChatMod.LOGGER.debug("  Description: {}", mixerInfo.getDescription());
                VoiceChatMod.LOGGER.debug("  Vendor: {}", mixerInfo.getVendor());
                VoiceChatMod.LOGGER.debug("  Version: {}", mixerInfo.getVersion());
            }
        }
        VoiceChatMod.LOGGER.info("--- End of Audio Input Mixer List ---");
    }

    public void setAudioSender(AudioSender sender) {
        this.audioSender = sender;
    }

    public void setCurrentPlayerName(String playerName) {
        this.currentPlayerName = playerName;
    }

    public void cleanup() {
        stopCapture();
        if (microphoneLine != null) {
            microphoneLine.close();
            microphoneLine = null;
        }
    }
}