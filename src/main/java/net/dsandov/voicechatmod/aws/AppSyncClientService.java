package net.dsandov.voicechatmod.aws;

import net.dsandov.voicechatmod.Config;
import net.dsandov.voicechatmod.VoiceChatMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.Base64;

/**
 * Service class for interacting with AWS AppSync GraphQL API.
 * Handles audio data transmission through GraphQL mutations and subscriptions.
 */
public class AppSyncClientService {
    private static final Gson gson = new Gson();
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(300); // 5 minutes timeout
    
    private volatile boolean isInitialized = false;
    private volatile boolean isConnecting = false;
    private volatile boolean isShuttingDown = false;
    private String apiKey;
    private URI appSyncEndpoint;
    private URI appSyncWsEndpoint;
    private HttpClient httpClient;
    private WebSocket webSocket;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AppSync-Service-Thread");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean isSubscribed = false;
    private ScheduledFuture<?> pingTask;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    private static class WebSocketProtocolHeader {
        private static final String WEBSOCKET_PROTOCOL = "graphql-ws";
        
        public static String[] getProtocols() {
            return new String[]{WEBSOCKET_PROTOCOL};
        }
    }

    public AppSyncClientService() {
        VoiceChatMod.LOGGER.info("Initializing AppSyncClientService...");
    }

    // WebSocket listener for handling subscription messages
    private class AudioSubscriptionListener implements WebSocket.Listener {
        private StringBuilder messageBuilder = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuilder.append(data);
            
            if (last) {
                String message = messageBuilder.toString();
                messageBuilder = new StringBuilder();
                
                try {
                    JsonObject jsonMessage = gson.fromJson(message, JsonObject.class);
                    VoiceChatMod.LOGGER.debug("Received WebSocket message: {}", message);
                    
                    if (jsonMessage.has("type")) {
                        String type = jsonMessage.get("type").getAsString();
                        
                        switch (type) {
                            case "connection_ack":
                                VoiceChatMod.LOGGER.info("WebSocket connection acknowledged by AppSync");
                                reconnectAttempts = 0; // Reset reconnect counter on successful connection
                                startSubscription();
                                break;
                                
                            case "connection_error":
                                VoiceChatMod.LOGGER.error("WebSocket connection error: {}", message);
                                if (jsonMessage.has("payload")) {
                                    JsonObject errorPayload = jsonMessage.getAsJsonObject("payload");
                                    VoiceChatMod.LOGGER.error("Error details: {}", errorPayload);
                                }
                                handleConnectionError();
                                break;
                                
                            case "data":
                                if (jsonMessage.has("payload") && jsonMessage.getAsJsonObject("payload").has("data")) {
                                    JsonObject audioData = jsonMessage.getAsJsonObject("payload")
                                                               .getAsJsonObject("data")
                                                               .getAsJsonObject("onReceiveAudio");
                                    
                                    String audioContent = audioData.get("data").getAsString();
                                    String author = audioData.get("author").getAsString();
                                    String format = audioData.get("format").getAsString();
                                    String encoding = audioData.get("encoding").getAsString();
                                    
                                    VoiceChatMod.LOGGER.debug("Received audio data - Format: {}, Encoding: {}, Author: {}, Size: {} bytes",
                                        format, encoding, author, audioContent.length());
                                    
                                    // Only process audio from other players
                                    if (!author.equals(VoiceChatMod.getCurrentPlayerName())) {
                                        VoiceChatMod.LOGGER.debug("Processing received audio from {}", author);
                                        VoiceChatMod.ClientModEvents.processReceivedAudio(audioContent, author);
                                    }
                                }
                                break;
                                
                            case "ka":
                                VoiceChatMod.LOGGER.debug("Received keep-alive message");
                                sendPing();
                                break;
                                
                            case "error":
                                VoiceChatMod.LOGGER.error("Received error message: {}", message);
                                if (jsonMessage.has("payload")) {
                                    JsonObject errorPayload = jsonMessage.getAsJsonObject("payload");
                                    VoiceChatMod.LOGGER.error("Error details: {}", errorPayload);
                                    
                                    if (shouldReconnectOnError(errorPayload)) {
                                        handleConnectionError();
                                    }
                                }
                                break;
                                
                            default:
                                VoiceChatMod.LOGGER.debug("Received WebSocket message of type: {}", type);
                                break;
                        }
                    }
                } catch (Exception e) {
                    VoiceChatMod.LOGGER.error("Error processing WebSocket message: {}", message, e);
                }
            }
            
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            VoiceChatMod.LOGGER.error("WebSocket error", error);
            if (error instanceof java.net.http.WebSocketHandshakeException) {
                java.net.http.WebSocketHandshakeException handshakeError = (java.net.http.WebSocketHandshakeException) error;
                VoiceChatMod.LOGGER.error("WebSocket handshake failed. Response code: {}", handshakeError.getResponse().statusCode());
                VoiceChatMod.LOGGER.error("Response headers: {}", handshakeError.getResponse().headers().map());
            }
            handleConnectionError();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            VoiceChatMod.LOGGER.info("WebSocket closed: {} - {}", statusCode, reason);
            isSubscribed = false;
            if (statusCode != WebSocket.NORMAL_CLOSURE && !isShuttingDown) {
                VoiceChatMod.LOGGER.warn("Abnormal WebSocket closure. Attempting to reconnect...");
                handleConnectionError();
            }
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }

    private void handleConnectionError() {
        if (isShuttingDown) {
            VoiceChatMod.LOGGER.info("Service is shutting down, skipping reconnection.");
            return;
        }

        reconnectAttempts++;
        if (reconnectAttempts <= MAX_RECONNECT_ATTEMPTS) {
            long delayMs = Math.min(1000 * (long)Math.pow(2, reconnectAttempts), 30000); // Exponential backoff with max 30s
            VoiceChatMod.LOGGER.info("Scheduling WebSocket reconnection attempt {}/{} in {} ms...", 
                reconnectAttempts, MAX_RECONNECT_ATTEMPTS, delayMs);
            
            executorService.schedule(() -> {
                if (!isShuttingDown) {
                    setupWebSocket();
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        } else {
            VoiceChatMod.LOGGER.error("Maximum reconnection attempts ({}) reached. Manual intervention may be required.", 
                MAX_RECONNECT_ATTEMPTS);
        }
    }

    private void startSubscription() {
        if (isShuttingDown || isSubscribed) {
            return;
        }

        // Create the subscription message for audio data
        JsonObject variables = new JsonObject();
        variables.addProperty("channel", "global");

        JsonObject payload = new JsonObject();
        payload.addProperty("query", 
            "subscription OnReceiveAudio($channel: String!) {" +
            "  onReceiveAudio(channel: $channel) {" +
            "    format" +
            "    encoding" +
            "    data" +
            "    author" +
            "  }" +
            "}");
        payload.add("variables", variables);

        JsonObject message = new JsonObject();
        message.addProperty("id", "1");
        message.addProperty("type", "start");
        message.add("payload", payload);

        String subscriptionMessage = gson.toJson(message);
        VoiceChatMod.LOGGER.debug("Starting subscription with message: {}", subscriptionMessage);
        webSocket.sendText(subscriptionMessage, true);
        isSubscribed = true;
        VoiceChatMod.LOGGER.info("Started audio subscription");
    }

    private void sendPing() {
        if (webSocket != null && isSubscribed && !isShuttingDown) {
            webSocket.sendText("{\"type\":\"ping\"}", true);
        }
    }

    private CompletableFuture<Boolean> setupWebSocket() {
        if (isConnecting || isShuttingDown) {
            return CompletableFuture.completedFuture(false);
        }

        isConnecting = true;
        try {
            // Construct the real-time endpoint URL using the HTTP endpoint
            String host = appSyncEndpoint.getHost();
            String[] parts = host.split("\\.");
            if (parts.length < 4) {
                VoiceChatMod.LOGGER.error("Invalid AppSync endpoint format");
                isConnecting = false;
                return CompletableFuture.completedFuture(false);
            }
            
            String apiId = parts[0];
            String region = Config.appSyncApiRegion;
            
            // Validate the region
            if (region == null || region.trim().isEmpty() || region.equals("amazonaws")) {
                VoiceChatMod.LOGGER.error("Invalid AppSync region: {}", region);
                isConnecting = false;
                return CompletableFuture.completedFuture(false);
            }

            // Create the header object
            JsonObject header = new JsonObject();
            header.addProperty("host", host);
            header.addProperty("x-api-key", apiKey);

            // Base64 encode the header
            String encodedHeader = Base64.getEncoder().encodeToString(header.toString().getBytes());
            
            // Construct the WebSocket URL with the correct domain format and protocol parameter
            String wsUrl = String.format("wss://%s.appsync-realtime-api.%s.amazonaws.com/graphql?header=%s&payload=e30=", 
                apiId, region, encodedHeader);
            VoiceChatMod.LOGGER.debug("Using WebSocket URL: {}", wsUrl);
            
            try {
                appSyncWsEndpoint = new URI(wsUrl);
            } catch (URISyntaxException e) {
                VoiceChatMod.LOGGER.error("Failed to parse WebSocket URL", e);
                isConnecting = false;
                return CompletableFuture.completedFuture(false);
            }

            // Create the connection init message with the correct format for AppSync
            JsonObject initMessage = new JsonObject();
            initMessage.addProperty("type", "connection_init");
            initMessage.add("payload", new JsonObject()); // Empty payload as per AppSync requirements

            String initMessageStr = gson.toJson(initMessage);

            // Build and connect WebSocket with proper settings
            VoiceChatMod.LOGGER.info("Attempting to establish WebSocket connection to: {}", appSyncWsEndpoint);
            
            return httpClient.newWebSocketBuilder()
                .connectTimeout(CONNECTION_TIMEOUT)
                .subprotocols("graphql-ws")
                .buildAsync(appSyncWsEndpoint, new AudioSubscriptionListener())
                .thenApply(ws -> {
                    webSocket = ws;
                    isConnecting = false;
                    VoiceChatMod.LOGGER.info("WebSocket connection established successfully");
                    ws.sendText(initMessageStr, true);
                    return true;
                })
                .exceptionally(throwable -> {
                    isConnecting = false;
                    VoiceChatMod.LOGGER.error("Failed to setup WebSocket", throwable);
                    if (throwable.getCause() instanceof java.net.http.WebSocketHandshakeException) {
                        java.net.http.WebSocketHandshakeException wsException = 
                            (java.net.http.WebSocketHandshakeException) throwable.getCause();
                        VoiceChatMod.LOGGER.error("WebSocket handshake failed. Response code: {}", 
                            wsException.getResponse().statusCode());
                        VoiceChatMod.LOGGER.error("Response headers: {}", 
                            wsException.getResponse().headers().map());
                    }
                    return false;
                })
                .thenApply(success -> {
                    if (success && !isShuttingDown) {
                        // Schedule periodic pings
                        if (pingTask != null) {
                            pingTask.cancel(false);
                        }
                        pingTask = executorService.scheduleAtFixedRate(
                            this::sendPing,
                            0,
                            45,
                            TimeUnit.SECONDS
                        );
                    }
                    return success;
                });

        } catch (Exception e) {
            isConnecting = false;
            VoiceChatMod.LOGGER.error("Failed to setup WebSocket", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Boolean> initializeAndConnect() {
        if (isInitialized || isConnecting || isShuttingDown) {
            return CompletableFuture.completedFuture(isInitialized);
        }

        VoiceChatMod.LOGGER.info("Starting AppSync service initialization...");

        try {
            // Load and validate configuration
            this.apiKey = Config.appSyncApiKey;
            this.appSyncEndpoint = new URI(Config.voiceServerUrl);

            // Validate configuration
            if (!validateConfig()) {
                VoiceChatMod.LOGGER.error("AppSync configuration validation failed.");
                return CompletableFuture.completedFuture(false);
            }

            // Initialize Java HTTP client for GraphQL requests with improved settings
            this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(CONNECTION_TIMEOUT)
                .executor(executorService)
                .build();

            // Setup WebSocket connection for subscriptions and wait for it to complete
            return setupWebSocket().thenApply(success -> {
                if (success) {
                    isInitialized = true;
                    VoiceChatMod.LOGGER.info("AppSync service initialized successfully.");
                } else {
                    VoiceChatMod.LOGGER.error("Failed to initialize AppSync service - WebSocket setup failed.");
                }
                return success;
            });

        } catch (URISyntaxException e) {
            VoiceChatMod.LOGGER.error("Invalid AppSync endpoint URL", e);
            return CompletableFuture.completedFuture(false);
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to initialize AppSync service", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    private boolean validateConfig() {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("YOUR_APPSYNC_API_KEY_HERE")) {
            VoiceChatMod.LOGGER.error("Invalid or missing AppSync API key");
            return false;
        }

        if (appSyncEndpoint == null || !appSyncEndpoint.toString().contains("appsync-api")) {
            VoiceChatMod.LOGGER.error("Invalid AppSync endpoint URL");
            return false;
        }

        return true;
    }

    public boolean sendAudioMutation(String channel, String format, String encoding, String data, String author) {
        if (!isInitialized) {
            VoiceChatMod.LOGGER.error("Cannot send audio mutation - service not initialized");
            return false;
        }

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Construct GraphQL mutation
                String mutation = String.format(
                    "mutation SendAudio($channel: String!, $format: String!, $encoding: String!, $data: String!, $author: String!) { " +
                    "  sendAudio(" +
                    "    method: \"SEND\"," +  // Add required method field
                    "    channel: $channel," +
                    "    format: $format," +
                    "    encoding: $encoding," +
                    "    data: $data," +
                    "    author: $author" +
                    "  ) { " +
                    "    format " +
                    "    encoding " +
                    "    data " +
                    "    author " +
                    "  } " +
                    "}");

                // Create variables object
                JsonObject variables = new JsonObject();
                variables.addProperty("channel", channel);
                variables.addProperty("format", format);
                variables.addProperty("encoding", encoding);
                variables.addProperty("data", data);
                variables.addProperty("author", author);

                // Create request payload
                JsonObject payload = new JsonObject();
                payload.addProperty("query", mutation);
                payload.add("variables", variables);

                // Create HTTP request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(appSyncEndpoint)
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();

                // Send request
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // Check response
                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                    if (jsonResponse.has("errors")) {
                        VoiceChatMod.LOGGER.error("GraphQL error in sendAudio mutation: {}", response.body());
                        // Only retry on certain types of errors
                        if (shouldRetryError(jsonResponse) && attempt < MAX_RETRIES - 1) {
                            Thread.sleep(RETRY_DELAY.toMillis());
                            continue;
                        }
                        return false;
                    }
                    VoiceChatMod.LOGGER.debug("Successfully sent audio mutation");
                    return true;
                } else if (isRetryableStatusCode(response.statusCode()) && attempt < MAX_RETRIES - 1) {
                    VoiceChatMod.LOGGER.warn("Retryable error sending audio mutation. Status: {}, Response: {}", 
                        response.statusCode(), response.body());
                    Thread.sleep(RETRY_DELAY.toMillis());
                } else {
                    VoiceChatMod.LOGGER.error("Failed to send audio mutation. Status: {}, Response: {}", 
                        response.statusCode(), response.body());
                    return false;
                }

            } catch (Exception e) {
                VoiceChatMod.LOGGER.error("Error sending audio mutation (attempt {})", attempt + 1, e);
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private boolean shouldRetryError(JsonObject response) {
        try {
            JsonObject error = response.getAsJsonArray("errors").get(0).getAsJsonObject();
            String errorType = error.has("errorType") ? error.get("errorType").getAsString() : "";
            // Retry on connection errors, throttling, or temporary server issues
            return errorType.contains("Connection") || 
                   errorType.contains("Throttle") || 
                   errorType.contains("Server");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isRetryableStatusCode(int statusCode) {
        // Retry on server errors (500s) and some client errors (429 - too many requests)
        return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }

    private boolean shouldReconnectOnError(JsonObject errorPayload) {
        try {
            if (errorPayload.has("errors")) {
                for (var error : errorPayload.getAsJsonArray("errors")) {
                    JsonObject errorObj = error.getAsJsonObject();
                    String message = errorObj.has("message") ? errorObj.get("message").getAsString() : "";
                    String errorType = errorObj.has("errorType") ? errorObj.get("errorType").getAsString() : "";
                    
                    // Don't reconnect on protocol errors or unsupported operations
                    if (message.equals("NoProtocolError") || 
                        errorType.equals("UnsupportedOperation") ||
                        message.contains("not supported through the realtime channel")) {
                        VoiceChatMod.LOGGER.error("Non-recoverable error detected: {}. Please check configuration.", message);
                        return false;
                    }
                }
            }
            
            // Reconnect on other types of errors
            return true;
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error checking reconnection condition", e);
            return false;
        }
    }

    public void disconnect() {
        isShuttingDown = true;
        
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnecting");
            } catch (Exception e) {
                VoiceChatMod.LOGGER.error("Error closing WebSocket", e);
            } finally {
                webSocket = null;
            }
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        isInitialized = false;
        isSubscribed = false;
        isConnecting = false;
        reconnectAttempts = 0;
        VoiceChatMod.LOGGER.info("AppSync service disconnected.");
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    // Simple test method to verify AppSync connectivity
    public boolean sendTestMessage(String message) {
        if (!isInitialized) {
            VoiceChatMod.LOGGER.error("Cannot send test message - service not initialized");
            return false;
        }

        try {
            VoiceChatMod.LOGGER.info("=== STARTING APPSYNC TEST ===");
            
            // Use a simpler test mutation that matches the AppSync schema
            String mutation = String.format(
                "mutation SendTestAudio { " +
                "  sendAudio(" +
                "    method: \"TEST\"," +  // Add required method field
                "    channel: \"test\"," +
                "    format: \"test\"," +
                "    encoding: \"none\"," +
                "    data: \"%s\"," +
                "    author: \"%s\"" +
                "  ) { " +
                "    format " +
                "    encoding " +
                "    data " +
                "    author " +
                "  } " +
                "}", message, VoiceChatMod.getCurrentPlayerName());

            // Create request payload
            JsonObject payload = new JsonObject();
            payload.addProperty("query", mutation);

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(appSyncEndpoint)
                .header("Content-Type", "application/json")
                .header("X-Api-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

            VoiceChatMod.LOGGER.info("Sending test mutation to AppSync...");
            VoiceChatMod.LOGGER.debug("Test mutation: {}", mutation);  // Log the mutation for debugging
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            VoiceChatMod.LOGGER.info("=== TEST RESPONSE ===");
            VoiceChatMod.LOGGER.info("Status code: {}", response.statusCode());
            VoiceChatMod.LOGGER.info("Response body: {}", response.body());
            VoiceChatMod.LOGGER.info("=== END TEST ===");
            
            // Check for errors in the response
            JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
            return response.statusCode() == 200 && !jsonResponse.has("errors");
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("=== TEST FAILED ===");
            VoiceChatMod.LOGGER.error("Error: {}", e.getMessage());
            return false;
        }
    }
}