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
        "/game-server/websocket/stage-url",
        "/game-server/websocket/api-key",
        "/game-server/cognito/user-pool-id",
        "/game-server/cognito/user-pool-client-id"
    };

    private static final String[] TOML_KEYS = {
        "websocketStageUrl",
        "websocketApiKey",
        "userPoolId",
        "userPoolClientId"
    };

    public static void updateConfigFromSSM(String configPath) {
        try {
            // Read AWS credentials
            String[] credentials = readAWSCredentials();
            if (credentials == null) {
                VoiceChatMod.LOGGER.error("AWS credentials not found in {}", AWS_CREDENTIALS_PATH);
                return;
            }
            String accessKeyId = credentials[0];
            String secretKey = credentials[1];

            // Read current TOML content
            List<String> lines = new ArrayList<>();
            if (Files.exists(Paths.get(configPath))) {
                lines = Files.readAllLines(Paths.get(configPath));
            }

            // Get SSM parameters
            for (int i = 0; i < SSM_PARAMETERS.length; i++) {
                String paramValue = getSSMParameter(SSM_PARAMETERS[i], accessKeyId, secretKey);
                if (paramValue != null) {
                    updateTomlValue(lines, TOML_KEYS[i], paramValue);
                }
            }

            // Write updated content back to file
            Files.write(Paths.get(configPath), lines);
            VoiceChatMod.LOGGER.info("Configuration updated successfully from SSM parameters");

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
            String amzTarget = "AmazonSSM.GetParameter";
            
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("Name", parameterName);
            requestBody.addProperty("WithDecryption", true);
            
            String requestBodyStr = requestBody.toString();
            
            // Get current timestamp
            String amzDate = Instant.now().toString().substring(0, 16).replace("-", "").replace(":", "") + "Z";
            String dateStamp = amzDate.substring(0, 8);
            
            // Create canonical request
            String canonicalHeaders = String.format(
                "content-type:application/x-amz-json-1.1\n" +
                "host:ssm.%s.amazonaws.com\n" +
                "x-amz-date:%s\n" +
                "x-amz-target:%s\n",
                AWS_REGION, amzDate, amzTarget
            );
            String signedHeaders = "content-type;host;x-amz-date;x-amz-target";
            
            // Create signature
            String algorithm = "AWS4-HMAC-SHA256";
            String credentialScope = String.format("%s/%s/%s/aws4_request", dateStamp, AWS_REGION, AWS_SERVICE);
            
            String stringToSign = String.format(
                "%s\n%s\n%s\n%s",
                algorithm,
                amzDate,
                credentialScope,
                canonicalHeaders
            );
            
            byte[] kSecret = ("AWS4" + secretKey).getBytes();
            byte[] kDate = hmacSHA256(dateStamp, kSecret);
            byte[] kRegion = hmacSHA256(AWS_REGION, kDate);
            byte[] kService = hmacSHA256(AWS_SERVICE, kRegion);
            byte[] kSigning = hmacSHA256("aws4_request", kService);
            byte[] signature = hmacSHA256(stringToSign, kSigning);
            
            String authorization = String.format(
                "%s Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                algorithm,
                accessKeyId,
                credentialScope,
                signedHeaders,
                bytesToHex(signature)
            );
            
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
} 