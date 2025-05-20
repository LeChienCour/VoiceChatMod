// src/main/java/net/dsandov/voicechatmod/aws/VoiceGatewayClient.java
package net.dsandov.voicechatmod.aws;

import net.dsandov.voicechatmod.Config; // For new configuration values
import net.dsandov.voicechatmod.VoiceChatMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles WebSocket communication with the AWS API Gateway Voice Gateway.
 * This class manages the WebSocket connection, sending audio data, and receiving messages.
 */
public class VoiceGatewayClient implements WebSocket.Listener {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final String gatewayUrl;
    private final String apiKey;
    private final int maxReconnectAttempts;
    private final int reconnectDelay;

    /**
     * Creates a new VoiceGatewayClient instance.
     * 
     * @param gatewayUrl The WebSocket URL for the Voice Gateway
     * @param apiKey Optional API key for authentication
     * @param maxReconnectAttempts Maximum number of reconnection attempts
     * @param reconnectDelay Delay between reconnection attempts in seconds
     */
    public VoiceGatewayClient(String gatewayUrl, String apiKey, int maxReconnectAttempts, int reconnectDelay) {
        this.gatewayUrl = gatewayUrl;
        this.apiKey = apiKey;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelay = reconnectDelay;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Initializes and connects to the Voice Gateway WebSocket.
     * 
     * @return CompletableFuture that completes when the connection is established
     */
    public CompletableFuture<Void> initializeAndConnect() {
        if (isConnected.get()) {
            VoiceChatMod.LOGGER.warn("Already connected to Voice Gateway");
            return CompletableFuture.completedFuture(null);
        }

        try {
            URI uri = new URI(gatewayUrl);
            CompletableFuture<WebSocket> webSocketFuture = httpClient.newWebSocketBuilder()
                    .header("x-api-key", apiKey) // Add API key if configured
                    .buildAsync(uri, this);

            return webSocketFuture.thenAccept(ws -> {
                this.webSocket = ws;
                isConnected.set(true);
                reconnectAttempts.set(0);
                VoiceChatMod.LOGGER.info("Connected to Voice Gateway");
            });
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to connect to Voice Gateway", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Disconnects from the Voice Gateway WebSocket.
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnecting");
            webSocket = null;
            isConnected.set(false);
            VoiceChatMod.LOGGER.info("Disconnected from Voice Gateway");
        }
    }

    /**
     * Sends audio data to the Voice Gateway.
     * 
     * @param channel The channel to send the audio to
     * @param format The audio format (e.g., "opus")
     * @param encoding The audio encoding (e.g., "base64")
     * @param base64Data The base64-encoded audio data
     * @param author The author of the audio
     * @param timestamp The timestamp of the audio
     * @param clientMethodContext Additional context for the client method
     */
    public void sendAudioData(String channel, String format, String encoding, String base64Data,
                            String author, String timestamp, String clientMethodContext) {
        if (!isConnected.get()) {
            VoiceChatMod.LOGGER.warn("Cannot send audio data: Not connected to Voice Gateway");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("action", "sendaudio");
        payload.addProperty("channel", channel);
        payload.addProperty("format", format);
        payload.addProperty("encoding", encoding);
        payload.addProperty("data", base64Data);
        payload.addProperty("author", author);
        payload.addProperty("timestamp", timestamp);
        payload.addProperty("context", clientMethodContext);

        String message = GSON.toJson(payload);
        webSocket.sendText(message, true);
    }

    /**
     * Checks if the client is connected to the Voice Gateway.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return isConnected.get();
    }

    // WebSocket.Listener implementation
    @Override
    public void onOpen(WebSocket webSocket) {
        VoiceChatMod.LOGGER.info("WebSocket connection opened");
        isConnected.set(true);
        reconnectAttempts.set(0);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            JsonObject message = GSON.fromJson(data.toString(), JsonObject.class);
            String action = message.get("action").getAsString();

            switch (action) {
                case "audio":
                    handleAudioMessage(message);
                    break;
                case "notification":
                    handleNotificationMessage(message);
                    break;
                default:
                    VoiceChatMod.LOGGER.warn("Received unknown message action: {}", action);
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error processing WebSocket message", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        VoiceChatMod.LOGGER.info("WebSocket connection closed: {} - {}", statusCode, reason);
        isConnected.set(false);
        attemptReconnect();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        VoiceChatMod.LOGGER.error("WebSocket error", error);
        isConnected.set(false);
        attemptReconnect();
    }

    private void handleAudioMessage(JsonObject message) {
        // Forward audio data to the mod's audio processing system
        Minecraft.getInstance().execute(() -> {
            VoiceChatMod.ClientModEvents.processReceivedAudio(
                message.get("data").getAsString(),
                message.get("format").getAsString(),
                message.get("encoding").getAsString(),
                message.get("author").getAsString(),
                message.get("timestamp").getAsString(),
                message.get("context").getAsString()
            );
        });
    }

    private void handleNotificationMessage(JsonObject message) {
        String type = message.get("type").getAsString();
        String content = message.get("content").getAsString();
        
        // Handle different types of notifications
        switch (type) {
            case "error":
                VoiceChatMod.LOGGER.error("Voice Gateway notification: {}", content);
                break;
            case "warning":
                VoiceChatMod.LOGGER.warn("Voice Gateway notification: {}", content);
                break;
            case "info":
                VoiceChatMod.LOGGER.info("Voice Gateway notification: {}", content);
                break;
            default:
                VoiceChatMod.LOGGER.info("Voice Gateway notification: {}", content);
        }
    }

    private void attemptReconnect() {
        if (reconnectAttempts.incrementAndGet() <= maxReconnectAttempts) {
            VoiceChatMod.LOGGER.info("Attempting to reconnect to Voice Gateway (attempt {}/{})",
                    reconnectAttempts.get(), maxReconnectAttempts);
            
            // Schedule reconnection attempt
            new Thread(() -> {
                try {
                    Thread.sleep(reconnectDelay * 1000L);
                    initializeAndConnect();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        } else {
            VoiceChatMod.LOGGER.error("Failed to reconnect to Voice Gateway after {} attempts",
                    maxReconnectAttempts);
        }
    }
}