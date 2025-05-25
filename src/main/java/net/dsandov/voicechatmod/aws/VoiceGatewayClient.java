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
import java.nio.ByteBuffer;
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
    private StringBuilder messageBuffer = new StringBuilder();

    // Message action constants
    private static final String ACTION_PING = "ping";
    private static final String ACTION_SEND_AUDIO = "sendaudio";
    private static final String ACTION_AUDIO = "audio";

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
            
            webSocket.sendText(message, true)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            VoiceChatMod.LOGGER.error("Failed to send audio: {}", error.getMessage());
                        }
                    });
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error sending audio data: {}", e.getMessage());
        }
    }

    /**
     * Gets the current WebSocket instance.
     * @return The current WebSocket instance
     */
    public WebSocket getWebSocket() {
        return webSocket;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        VoiceChatMod.LOGGER.debug("Received binary message: {} bytes", data.remaining());
        // Process binary audio data directly
        if (last) {
            byte[] audioData = new byte[data.remaining()];
            data.get(audioData);
            // Process the audio data as needed
            Minecraft.getInstance().execute(() -> {
                VoiceChatMod.ClientModEvents.processReceivedAudio(
                    java.util.Base64.getEncoder().encodeToString(audioData),
                    "pcm", "base64", "remote", 
                    java.time.Instant.now().toString(),
                    "binary"
                );
            });
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            String message = data.toString();
            // Only log chunk info if there's an issue
            if (!last) {
                VoiceChatMod.LOGGER.debug("Received partial message chunk: {} bytes", message.length());
            }
            
            messageBuffer.append(message);
            
            if (last) {
                String completeMessage = messageBuffer.toString();
                messageBuffer = new StringBuilder();
                
                try {
                    JsonObject jsonMessage = GSON.fromJson(completeMessage, JsonObject.class);
                    
                    if (jsonMessage.has("status") && jsonMessage.has("message")) {
                        String status = jsonMessage.get("status").getAsString();
                        JsonObject messageObj = jsonMessage.getAsJsonObject("message");
                        String action = messageObj.has("action") ? messageObj.get("action").getAsString() : "unknown";
                        
                        if (!"PROCESSED".equals(status)) {
                            VoiceChatMod.LOGGER.warn("Received message with non-processed status: {}", status);
                        }
                        
                        switch (action) {
                            case ACTION_SEND_AUDIO:
                            case ACTION_AUDIO:
                                if ("PROCESSED".equals(status)) {
                                    handleBroadcastAudio(messageObj);
                                }
                                break;
                            case "error":
                                VoiceChatMod.LOGGER.error("Server error: {}", 
                                    messageObj.has("message") ? messageObj.get("message").getAsString() : "No details");
                                break;
                            case "pong":
                                VoiceChatMod.LOGGER.debug("Received pong response");
                                break;
                            default:
                                VoiceChatMod.LOGGER.warn("Unknown action received: {}", action);
                        }
                    } else {
                        String action = jsonMessage.has("action") ? jsonMessage.get("action").getAsString() : "unknown";
                        
                        switch (action) {
                            case ACTION_SEND_AUDIO:
                            case ACTION_AUDIO:
                                handleBroadcastAudio(jsonMessage);
                                break;
                            case "pong":
                                VoiceChatMod.LOGGER.debug("Received pong response");
                                break;
                            case "error":
                                VoiceChatMod.LOGGER.error("Server error: {}", 
                                    jsonMessage.has("message") ? jsonMessage.get("message").getAsString() : "No details");
                                break;
                            default:
                                VoiceChatMod.LOGGER.warn("Unknown direct message action: {}", action);
                        }
                    }
                } catch (com.google.gson.JsonSyntaxException e) {
                    VoiceChatMod.LOGGER.error("Invalid JSON message format: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("WebSocket text processing error: {}", e.getMessage());
            messageBuffer = new StringBuilder();
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles broadcasted audio messages from the server.
     * Processes messages in the server-to-client format and forwards to the audio system.
     */
    private void handleBroadcastAudio(JsonObject message) {
        try {
            JsonObject dataObj = message.has("data") && message.get("data").isJsonObject() ?
                message.getAsJsonObject("data") :
                message;
            
            String audio = dataObj.has("audio") ? 
                dataObj.get("audio").getAsString() :
                dataObj.get("data").getAsString();
                
            String author = dataObj.get("author").getAsString();
            String timestamp = dataObj.has("timestamp") ? 
                dataObj.get("timestamp").getAsString() : 
                java.time.Instant.now().toString();
            
            String format = "pcm";
            String encoding = "base64";

            String currentPlayer = VoiceChatMod.getCurrentPlayerName();
            
            if (!author.equals(currentPlayer) || VoiceChatMod.ClientModEvents.isEchoEnabled()) {
                Minecraft.getInstance().execute(() -> {
                    try {
                        VoiceChatMod.ClientModEvents.processReceivedAudio(
                            audio, format, encoding, author, timestamp, "broadcast"
                        );
                    } catch (Exception e) {
                        VoiceChatMod.LOGGER.error("Error processing broadcast audio: {}", e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error handling broadcast message: {}", e.getMessage());
        }
    }

    /**
     * Sends a ping message to the server to test the connection.
     * This is meant to be called manually through the /vc ping command.
     */
    public void sendPingMessage() {
        if (!isConnected.get() || webSocket == null) {
            VoiceChatMod.LOGGER.error("Cannot send ping: Not connected to Voice Gateway");
            return;
        }
        
        try {
            JsonObject ping = new JsonObject();
            ping.addProperty("action", ACTION_PING);
            
            JsonObject pingData = new JsonObject();
            pingData.addProperty("timestamp", java.time.Instant.now().toString());
            ping.add("data", pingData);
            
            String pingMessage = GSON.toJson(ping);
            VoiceChatMod.LOGGER.info("Sending ping message: {}", pingMessage);
            
            webSocket.sendText(pingMessage, true)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        VoiceChatMod.LOGGER.error("Failed to send ping: {}", error.getMessage());
                        handleConnectionFailure();
                    } else {
                        VoiceChatMod.LOGGER.debug("Ping sent successfully, waiting for pong response...");
                    }
                });
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to send ping: {}", e.getMessage());
            handleConnectionFailure();
        }
    }

    private void handleConnectionFailure() {
        VoiceChatMod.LOGGER.warn("Connection failure detected");
        isConnected.set(false);
        attemptReconnect();
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        VoiceChatMod.LOGGER.info("WebSocket connection established");
        VoiceChatMod.LOGGER.info("Connection URL: {}", gatewayUrl);
        VoiceChatMod.LOGGER.info("API Key configured: {}", apiKey != null && !apiKey.isEmpty());
        webSocket.request(1);
        
        isConnected.set(true);
        reconnectAttempts.set(0);
        
        // Send a test message to verify the connection
        try {
            JsonObject testMessage = new JsonObject();
            testMessage.addProperty("action", "test");
            testMessage.addProperty("timestamp", java.time.Instant.now().toString());
            String testMessageStr = GSON.toJson(testMessage);
            VoiceChatMod.LOGGER.info("Sending test message: {}", testMessageStr);
            
            webSocket.sendText(testMessageStr, true)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        VoiceChatMod.LOGGER.error("Failed to send test message: {}", error.getMessage());
                    } else {
                        VoiceChatMod.LOGGER.info("Test message sent successfully");
                    }
                });
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error sending test message: {}", e.getMessage());
        }
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