// src/main/java/net/dsandov/voicechatmod/aws/VoiceGatewayClient.java
package net.dsandov.voicechatmod.aws;

import net.dsandov.voicechatmod.Config;
import net.dsandov.voicechatmod.VoiceChatMod;
import net.dsandov.voicechatmod.audio.AudioSender;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles WebSocket communication with the AWS API Gateway Voice Gateway.
 * This class manages real-time WebSocket connections for voice chat, including:
 * - Connection establishment and maintenance
 * - Sending audio data in the required JSON format
 * - Receiving and processing broadcasted audio messages
 * - Connection lifecycle management and error handling
 */
public class VoiceGatewayClient implements WebSocket.Listener, AudioSender {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final String gatewayUrl;
    private final String apiKey;
    private final int maxReconnectAttempts;
    private final int reconnectDelay;

    // Message action constants
    private static final String ACTION_SEND_AUDIO = "sendaudio";
    private static final String ACTION_AUDIO = "audio";
    private static final String ACTION_PING = "ping";

    /**
     * Creates a new VoiceGatewayClient instance.
     * 
     * @param gatewayUrl The WebSocket URL for the API Gateway endpoint
     * @param apiKey API key for authentication
     * @param maxReconnectAttempts Maximum number of reconnection attempts
     * @param reconnectDelay Delay between reconnection attempts in seconds
     */
    public VoiceGatewayClient(String gatewayUrl, String apiKey, int maxReconnectAttempts, int reconnectDelay) {
        this.gatewayUrl = gatewayUrl;
        this.apiKey = apiKey;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelay = reconnectDelay;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Initializes and connects to the Voice Gateway WebSocket.
     * Includes API key authentication in the connection request.
     */
    public CompletableFuture<Void> initializeAndConnect() {
        if (isConnected.get()) {
            VoiceChatMod.LOGGER.warn("Already connected to Voice Gateway");
            return CompletableFuture.completedFuture(null);
        }

        try {
            URI uri = new URI(gatewayUrl);
            CompletableFuture<WebSocket> webSocketFuture = httpClient.newWebSocketBuilder()
                    .header("x-api-key", apiKey)
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, this);

            return webSocketFuture.thenAccept(ws -> {
                this.webSocket = ws;
                isConnected.set(true);
                reconnectAttempts.set(0);
                VoiceChatMod.LOGGER.info("Connected to Voice Gateway at: {}", gatewayUrl);
                sendPingMessage(); // Send initial ping to verify connection
            });
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to connect to Voice Gateway", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Sends audio data to the Voice Gateway in the required JSON format.
     * Format matches the client-to-server message structure defined in the API.
     */
    @Override
    public void sendAudioData(String base64Audio, String author, String timestamp, String format, String encoding) {
        if (!isConnected.get() || webSocket == null) {
            VoiceChatMod.LOGGER.error("Cannot send audio: Not connected to Voice Gateway");
            VoiceChatMod.LOGGER.info("Connection state - Connected: {}, WebSocket: {}, Attempts: {}", 
                isConnected.get(), webSocket != null ? "Active" : "Null", reconnectAttempts.get());
            
            if (reconnectAttempts.get() < maxReconnectAttempts) {
                VoiceChatMod.LOGGER.info("Attempting to reconnect...");
                attemptReconnect();
            }
            return;
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("action", ACTION_SEND_AUDIO);
            payload.addProperty("data", base64Audio);
            payload.addProperty("author", author);
            payload.addProperty("timestamp", timestamp);
            payload.addProperty("format", format);
            payload.addProperty("encoding", encoding);

            String message = GSON.toJson(payload);
            VoiceChatMod.LOGGER.debug("Sending audio - Author: {}, Format: {}, Size: {} bytes", 
                author, format, base64Audio.length());
            
            webSocket.sendText(message, true)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            VoiceChatMod.LOGGER.error("Failed to send audio: {}", error.getMessage());
                        } else {
                            VoiceChatMod.LOGGER.debug("Audio sent successfully");
                        }
                    });
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error sending audio data", e);
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            String fullMessage = data.toString();
            VoiceChatMod.LOGGER.debug("Received message: {}", 
                fullMessage.substring(0, Math.min(100, fullMessage.length())) + "...");
            
            JsonObject message = GSON.fromJson(fullMessage, JsonObject.class);
            String action = message.get("action").getAsString();
            
            switch (action) {
                case ACTION_AUDIO:
                    handleBroadcastAudio(message);
                    break;
                case "pong":
                    VoiceChatMod.LOGGER.debug("Received pong response");
                    break;
                default:
                    VoiceChatMod.LOGGER.debug("Received unknown action: {}", action);
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error processing message", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles broadcasted audio messages from the server.
     * Processes messages in the server-to-client format and forwards to the audio system.
     */
    private void handleBroadcastAudio(JsonObject message) {
        try {
            JsonObject data = message.getAsJsonObject("data");
            String audio = data.get("audio").getAsString();
            String author = data.get("author").getAsString();
            String timestamp = data.get("timestamp").getAsString();
            
            // Optional fields with defaults
            String format = data.has("format") ? data.get("format").getAsString() : "pcm";
            String encoding = data.has("encoding") ? data.get("encoding").getAsString() : "base64";

            VoiceChatMod.LOGGER.debug("Processing broadcast from: {}", author);
            
            // Get current player name
            String currentPlayer = VoiceChatMod.getCurrentPlayerName();
            
            // Only process if:
            // 1. The message is not from us, OR
            // 2. Echo is enabled and the message is from us
            if (!author.equals(currentPlayer) || VoiceChatMod.ClientModEvents.isEchoEnabled()) {
                // Process on the main Minecraft thread
                Minecraft.getInstance().execute(() -> {
                    try {
                        VoiceChatMod.ClientModEvents.processReceivedAudio(
                            audio, format, encoding, author, timestamp, "broadcast"
                        );
                    } catch (Exception e) {
                        VoiceChatMod.LOGGER.error("Error processing broadcast audio", e);
                    }
                });
            } else {
                VoiceChatMod.LOGGER.debug("Skipping own audio (echo disabled)");
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error handling broadcast message", e);
        }
    }

    private void sendPingMessage() {
        if (!isConnected.get() || webSocket == null) return;
        
        try {
            JsonObject ping = new JsonObject();
            ping.addProperty("action", ACTION_PING);
            ping.addProperty("timestamp", java.time.Instant.now().toString());
            webSocket.sendText(GSON.toJson(ping), true);
            VoiceChatMod.LOGGER.debug("Sent ping message");
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to send ping", e);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        VoiceChatMod.LOGGER.info("WebSocket connection established");
        isConnected.set(true);
        reconnectAttempts.set(0);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        VoiceChatMod.LOGGER.info("WebSocket closed: {} - {}", statusCode, reason);
        isConnected.set(false);
        attemptReconnect();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        VoiceChatMod.LOGGER.error("WebSocket error: {}", error.getMessage());
        isConnected.set(false);
        attemptReconnect();
    }

    private void attemptReconnect() {
        if (reconnectAttempts.incrementAndGet() <= maxReconnectAttempts) {
            VoiceChatMod.LOGGER.info("Reconnecting ({}/{})", reconnectAttempts.get(), maxReconnectAttempts);
            CompletableFuture.delayedExecutor(reconnectDelay, TimeUnit.SECONDS).execute(() -> {
                initializeAndConnect();
            });
        } else {
            VoiceChatMod.LOGGER.error("Failed to reconnect after {} attempts", maxReconnectAttempts);
        }
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnecting");
            webSocket = null;
            isConnected.set(false);
            VoiceChatMod.LOGGER.info("Disconnected from Voice Gateway");
        }
    }

    public boolean isConnected() {
        return isConnected.get() && webSocket != null;
    }
}