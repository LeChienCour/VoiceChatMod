package net.dsandov.voicechatmod.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dsandov.voicechatmod.VoiceChatMod;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ConfigUpdater {
    private static final String AWS_REGION = "us-east-1";
    private static final String AWS_SERVICE = "ssm";
    private static final String AWS_CREDENTIALS_PATH = System.getProperty("user.home") + "/.aws/credentials";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String[] SSM_PARAMETERS = {
        "/game-server/dev/websocket/stage-url",
        "/game-server/dev/websocket/api-key",
        "/game-server/dev/cognito/user-pool-id",
        "/game-server/dev/cognito/user-pool-client-id"
    };

    private static final String[] TOML_KEYS = {
        "websocketStageUrl",
        "websocketApiKey",
        "userPoolId",
        "userPoolClientId"
    };

    public static void updateConfigFromSSM(String configPath) {
        try {
            // Convert to Path object for proper path handling
            Path configFilePath = Paths.get(configPath).toAbsolutePath().normalize();
            VoiceChatMod.LOGGER.info("Attempting to update configuration at: {}", configFilePath);
            
            // Read AWS credentials
            VoiceChatMod.LOGGER.info("Reading AWS credentials from: {}", AWS_CREDENTIALS_PATH);
            String[] credentials = readAWSCredentials();
            if (credentials == null) {
                VoiceChatMod.LOGGER.error("AWS credentials not found in {}", AWS_CREDENTIALS_PATH);
                return;
            }
            VoiceChatMod.LOGGER.info("AWS credentials loaded successfully");
            String accessKeyId = credentials[0];
            String secretKey = credentials[1];

            // Read current TOML content
            List<String> lines = new ArrayList<>();
            if (Files.exists(configFilePath)) {
                lines = Files.readAllLines(configFilePath);
                VoiceChatMod.LOGGER.info("Reading configuration from: {}", configFilePath);
            } else {
                VoiceChatMod.LOGGER.error("Configuration file not found at: {}", configFilePath);
                return;
            }

            // Get SSM parameters
            VoiceChatMod.LOGGER.info("Starting SSM parameter retrieval...");
            int updatedParams = 0;
            for (int i = 0; i < SSM_PARAMETERS.length; i++) {
                VoiceChatMod.LOGGER.info("Fetching SSM parameter: {}", SSM_PARAMETERS[i]);
                String paramValue = getSSMParameter(SSM_PARAMETERS[i], accessKeyId, secretKey);
                if (paramValue != null) {
                    VoiceChatMod.LOGGER.info("Successfully retrieved value for: {}", SSM_PARAMETERS[i]);
                    updateTomlValue(lines, TOML_KEYS[i], paramValue);
                    updatedParams++;
                } else {
                    VoiceChatMod.LOGGER.warn("Failed to retrieve value for: {}", SSM_PARAMETERS[i]);
                }
            }

            // Write updated content back to file
            Files.write(configFilePath, lines);
            VoiceChatMod.LOGGER.info("Configuration updated successfully. Updated {} out of {} parameters", 
                updatedParams, SSM_PARAMETERS.length);

            // Log the current state of AWS configuration (without revealing values)
            logConfigurationState(lines);

            // Reload configuration and initialize voice gateway
            reloadConfigurationAndInitialize();

        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to update configuration from SSM", e);
        }
    }

    private static String[] readAWSCredentials() throws IOException {
        if (!Files.exists(Paths.get(AWS_CREDENTIALS_PATH))) {
            return null;
        }

        String accessKeyId = null;
        String secretKey = null;

        List<String> lines = Files.readAllLines(Paths.get(AWS_CREDENTIALS_PATH));
        for (String line : lines) {
            if (line.trim().startsWith("aws_access_key_id")) {
                accessKeyId = line.split("=")[1].trim();
            } else if (line.trim().startsWith("aws_secret_access_key")) {
                secretKey = line.split("=")[1].trim();
            }
        }

        return (accessKeyId != null && secretKey != null) ? new String[]{accessKeyId, secretKey} : null;
    }

    private static String getSSMParameter(String parameterName, String accessKeyId, String secretKey) {
        try {
            String endpoint = String.format("https://ssm.%s.amazonaws.com/", AWS_REGION);
            String host = String.format("ssm.%s.amazonaws.com", AWS_REGION);
            String amzTarget = "AmazonSSM.GetParameter";
            
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("Name", parameterName);
            requestBody.addProperty("WithDecryption", true);
            
            String requestBodyStr = requestBody.toString();
            
            // Get current timestamp in correct ISO-8601 basic format
            Instant now = Instant.now();
            String amzDate = String.format("%04d%02d%02dT%02d%02d%02dZ",
                now.atZone(java.time.ZoneOffset.UTC).getYear(),
                now.atZone(java.time.ZoneOffset.UTC).getMonthValue(),
                now.atZone(java.time.ZoneOffset.UTC).getDayOfMonth(),
                now.atZone(java.time.ZoneOffset.UTC).getHour(),
                now.atZone(java.time.ZoneOffset.UTC).getMinute(),
                now.atZone(java.time.ZoneOffset.UTC).getSecond());
            String dateStamp = amzDate.substring(0, 8);

            // Calculate request hash
            String hashedPayload = bytesToHex(hmacSHA256(requestBodyStr, null));

            // Create canonical request
            String canonicalRequest = String.join("\n",
                "POST",
                "/",
                "",
                "content-type:application/x-amz-json-1.1",
                "host:" + host,
                "x-amz-date:" + amzDate,
                "x-amz-target:" + amzTarget,
                "",
                "content-type;host;x-amz-date;x-amz-target",
                hashedPayload
            );

            // Create string to sign
            String algorithm = "AWS4-HMAC-SHA256";
            String credentialScope = dateStamp + "/" + AWS_REGION + "/" + AWS_SERVICE + "/aws4_request";
            String stringToSign = algorithm + "\n" +
                                amzDate + "\n" +
                                credentialScope + "\n" +
                                bytesToHex(hmacSHA256(canonicalRequest, null));

            // Calculate signing key
            byte[] kSecret = ("AWS4" + secretKey).getBytes("UTF-8");
            byte[] kDate = hmacSHA256(dateStamp, kSecret);
            byte[] kRegion = hmacSHA256(AWS_REGION, kDate);
            byte[] kService = hmacSHA256(AWS_SERVICE, kRegion);
            byte[] kSigning = hmacSHA256("aws4_request", kService);

            // Calculate signature
            String signature = bytesToHex(hmacSHA256(stringToSign, kSigning));

            // Create authorization header
            String authorization = algorithm + " " +
                                 "Credential=" + accessKeyId + "/" + credentialScope + ", " +
                                 "SignedHeaders=content-type;host;x-amz-date;x-amz-target, " +
                                 "Signature=" + signature;

            // Build and send request - Note: Host header is managed automatically by HttpClient
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/x-amz-json-1.1")
                .header("X-Amz-Date", amzDate)
                .header("X-Amz-Target", amzTarget)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                return jsonResponse.getAsJsonObject("Parameter").get("Value").getAsString();
            } else {
                VoiceChatMod.LOGGER.error("Failed to get SSM parameter: {} (Status: {})", parameterName, response.statusCode());
                VoiceChatMod.LOGGER.error("Response: {}", response.body());
                return null;
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to get SSM parameter: " + parameterName, e);
            return null;
        }
    }

    private static void updateTomlValue(List<String> lines, String key, String value) {
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith(key + " =")) {
                lines.set(i, key + " = \"" + value + "\"");
                found = true;
                break;
            }
        }
        
        if (!found) {
            // If key not found, add it under [aws] section
            int awsSection = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("[aws]")) {
                    awsSection = i;
                    break;
                }
            }
            
            if (awsSection == -1) {
                lines.add("");
                lines.add("[aws]");
                lines.add(key + " = \"" + value + "\"");
            } else {
                lines.add(awsSection + 1, key + " = \"" + value + "\"");
            }
        }
    }

    private static byte[] hmacSHA256(String data, byte[] key) throws Exception {
        if (data == null) return null;
        
        if (key == null) {
            // If no key provided, just do a regular SHA256
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return digest.digest(data.getBytes("UTF-8"));
        }

        String algorithm = "HmacSHA256";
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        return mac.doFinal(data.getBytes("UTF-8"));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private static void logConfigurationState(List<String> lines) {
        VoiceChatMod.LOGGER.info("Current AWS Configuration State:");
        for (String key : TOML_KEYS) {
            boolean isConfigured = false;
            for (String line : lines) {
                if (line.trim().startsWith(key + " =")) {
                    String value = line.substring(line.indexOf("=") + 1).trim();
                    isConfigured = !value.equals("\"\"") && !value.equals("");
                    VoiceChatMod.LOGGER.info("  {} is {}", key, isConfigured ? "configured" : "not configured");
                    break;
                }
            }
        }
    }

    private static void reloadConfigurationAndInitialize() {
        try {
            // Force configuration reload
            VoiceChatMod.LOGGER.info("Reloading configuration and initializing voice gateway...");
            VoiceChatMod.getInstance().reloadConfig();
            
            // Initialize voice gateway with new configuration
            VoiceChatMod.getInstance().initializeVoiceGateway();
            
            VoiceChatMod.LOGGER.info("Configuration reloaded and voice gateway initialized successfully");
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Failed to reload configuration and initialize voice gateway", e);
        }
    }

    /**
     * Checks if AWS credentials are available and valid.
     * @return true if credentials are found and valid, false otherwise
     */
    public static boolean checkAWSCredentials() {
        try {
            VoiceChatMod.LOGGER.info("Checking AWS credentials at: {}", AWS_CREDENTIALS_PATH);
            String[] credentials = readAWSCredentials();
            if (credentials == null) {
                VoiceChatMod.LOGGER.warn("AWS credentials not found in {}", AWS_CREDENTIALS_PATH);
                return false;
            }
            
            String accessKeyId = credentials[0];
            String secretKey = credentials[1];
            
            // Basic validation of credentials format
            if (accessKeyId == null || accessKeyId.trim().isEmpty() || 
                secretKey == null || secretKey.trim().isEmpty()) {
                VoiceChatMod.LOGGER.warn("AWS credentials found but are invalid or empty");
                return false;
            }
            
            VoiceChatMod.LOGGER.info("AWS credentials validation successful");
            return true;
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error checking AWS credentials", e);
            return false;
        }
    }
} 