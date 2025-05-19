package net.dsandov.voicechatmod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = VoiceChatMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config
{
    // The builder for creating the configuration specification.
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- Voice Chat Configuration Definitions ---
    // These are the definitions of our configuration options.
    // They will be used to generate the config file and to retrieve values.

    private static final ModConfigSpec.BooleanValue ENABLE_VOICE_CHAT_SPEC = BUILDER
            .comment("Enable or disable the voice chat functionality globally.")
            .define("enableVoiceChat", true);

    private static final ModConfigSpec.DoubleValue DEFAULT_VOLUME_SPEC = BUILDER
            .comment("Default voice chat volume (0.0 to 1.0). This might be overridden by client-side settings later.")
            .defineInRange("defaultVolume", 0.7, 0.0, 1.0);

    private static final ModConfigSpec.ConfigValue<String> VOICE_SERVER_URL_SPEC = BUILDER
            .comment("The GraphQL API URL for the AWS AppSync voice server (e.g., https://<id>.appsync-api.<region>.amazonaws.com/graphql).")
            .define("voiceServerUrl", "YOUR_APPSYNC_GRAPHQL_API_URL_HERE");

    private static final ModConfigSpec.ConfigValue<String> APPSYNC_API_KEY_SPEC = BUILDER
            .comment("The API key for AWS AppSync authentication.")
            .define("appSyncApiKey", "YOUR_APPSYNC_API_KEY_HERE");

    private static final ModConfigSpec.IntValue MAX_VOICE_DISTANCE_SPEC = BUILDER
            .comment("Maximum distance (in blocks) at which players can hear each other. Set to 0 for global chat (if server supports).")
            .defineInRange("maxVoiceDistance", 64, 0, 256); // Example range

    private static final ModConfigSpec.ConfigValue<String> APPSYNC_API_REGION_SPEC = BUILDER
            .comment("The AWS region where your AppSync API is deployed.")
            .define("appSyncApiRegion", "us-east-1");

    // The actual config spec
    public static final ModConfigSpec SPEC = BUILDER.build();

    // These fields will hold the loaded values
    public static boolean enableVoiceChat;
    public static double defaultVolume;
    public static int maxVoiceDistance;
    public static String voiceServerUrl;
    public static String appSyncApiKey;
    public static String appSyncApiRegion;

    /**
     * This method is automatically called by NeoForge when a mod config file for this mod is loaded or reloaded.
     * It updates the static fields with the latest values from the configuration.
     *
     */
    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Check if the loaded config is our common config.
        if (event.getConfig().getSpec() == Config.SPEC) {
            VoiceChatMod.LOGGER.info("Loading VoiceChatMod common configuration...");
            
            enableVoiceChat = ENABLE_VOICE_CHAT_SPEC.get();
            defaultVolume = DEFAULT_VOLUME_SPEC.get();
            maxVoiceDistance = MAX_VOICE_DISTANCE_SPEC.get();
            voiceServerUrl = VOICE_SERVER_URL_SPEC.get();
            appSyncApiKey = APPSYNC_API_KEY_SPEC.get();
            appSyncApiRegion = APPSYNC_API_REGION_SPEC.get();

            // Log raw values for debugging
            VoiceChatMod.LOGGER.debug("Raw configuration values:");
            VoiceChatMod.LOGGER.debug("- AppSync URL: {}", voiceServerUrl);
            VoiceChatMod.LOGGER.debug("- AppSync API Key: {}", appSyncApiKey != null ? appSyncApiKey.substring(0, 6) + "..." : "null");
            VoiceChatMod.LOGGER.debug("- AppSync Region: {}", appSyncApiRegion);

            // Validate AppSync configuration
            boolean configValid = true;
            if (voiceServerUrl == null || voiceServerUrl.equals("YOUR_APPSYNC_GRAPHQL_API_URL_HERE")) {
                VoiceChatMod.LOGGER.error("AppSync GraphQL URL is not configured in voicechatmod-common.toml!");
                configValid = false;
            }
            if (appSyncApiKey == null || appSyncApiKey.equals("YOUR_APPSYNC_API_KEY_HERE")) {
                VoiceChatMod.LOGGER.error("AppSync API Key is not configured in voicechatmod-common.toml!");
                configValid = false;
            }
            if (appSyncApiRegion == null || appSyncApiRegion.trim().isEmpty()) {
                VoiceChatMod.LOGGER.error("AppSync Region is not configured in voicechatmod-common.toml!");
                configValid = false;
            }

            // Extract region from URL if not explicitly configured
            if (configValid && voiceServerUrl != null) {
                try {
                    String[] urlParts = new java.net.URI(voiceServerUrl).getHost().split("\\.");
                    if (urlParts.length >= 4) {
                        String urlRegion = urlParts[3];
                        if (!urlRegion.equals("amazonaws")) {
                            if (!urlRegion.equals(appSyncApiRegion)) {
                                VoiceChatMod.LOGGER.warn("AppSync Region in config ({}) doesn't match URL region ({}). Using URL region.", 
                                    appSyncApiRegion, urlRegion);
                                appSyncApiRegion = urlRegion;
                            }
                        } else {
                            VoiceChatMod.LOGGER.error("Invalid region extracted from URL: {}", urlRegion);
                            configValid = false;
                        }
                    }
                } catch (Exception e) {
                    VoiceChatMod.LOGGER.error("Failed to parse region from URL", e);
                    configValid = false;
                }
            }

            // Log configuration status
            VoiceChatMod.LOGGER.info("Voice Chat Configuration Status:");
            VoiceChatMod.LOGGER.info("- Voice Chat Enabled: {}", enableVoiceChat);
            VoiceChatMod.LOGGER.info("- Default Volume: {}", defaultVolume);
            VoiceChatMod.LOGGER.info("- Max Voice Distance: {}", maxVoiceDistance);
            VoiceChatMod.LOGGER.info("- AppSync Region: {}", appSyncApiRegion);
            VoiceChatMod.LOGGER.info("- AppSync URL configured: {}", voiceServerUrl != null && !voiceServerUrl.equals("YOUR_APPSYNC_GRAPHQL_API_URL_HERE"));
            VoiceChatMod.LOGGER.info("- AppSync API Key configured: {}", appSyncApiKey != null && !appSyncApiKey.equals("YOUR_APPSYNC_API_KEY_HERE"));
            VoiceChatMod.LOGGER.info("- Configuration valid: {}", configValid);

            if (!configValid) {
                VoiceChatMod.LOGGER.error("Please configure the AppSync settings in config/voicechatmod-common.toml!");
            } else {
                VoiceChatMod.LOGGER.info("AppSync configuration loaded and validated successfully.");
            }
        } else {
            VoiceChatMod.LOGGER.debug("Ignoring config load event for non-matching spec: {}", event.getConfig().getFileName());
        }
    }
}
