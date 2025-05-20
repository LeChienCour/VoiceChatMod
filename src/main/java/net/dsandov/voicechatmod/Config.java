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

    // Voice Chat Configuration
    private static final ModConfigSpec.BooleanValue ENABLE_VOICE_CHAT_SPEC = BUILDER
            .comment("Enable or disable the voice chat functionality globally.")
            .define("enableVoiceChat", true);

    private static final ModConfigSpec.DoubleValue DEFAULT_VOLUME_SPEC = BUILDER
            .comment("Default voice chat volume (0.0 to 1.0). This might be overridden by client-side settings later.")
            .defineInRange("defaultVolume", 0.7, 0.0, 1.0);

    private static final ModConfigSpec.IntValue MAX_VOICE_DISTANCE_SPEC = BUILDER
            .comment("Maximum distance (in blocks) at which players can hear each other. Set to 0 for global chat (if server supports).")
            .defineInRange("maxVoiceDistance", 64, 0, 256);

    private static final ModConfigSpec.IntValue RECONNECTION_ATTEMPTS_SPEC = BUILDER
            .comment("Number of times to attempt reconnection to the voice gateway if connection is lost.")
            .defineInRange("reconnectionAttempts", 3, 0, 10);

    private static final ModConfigSpec.IntValue RECONNECTION_DELAY_SPEC = BUILDER
            .comment("Delay in seconds between reconnection attempts.")
            .defineInRange("reconnectionDelay", 5, 1, 30);

    // AWS Configuration
    private static final ModConfigSpec.ConfigValue<String> WEBSOCKET_STAGE_URL_SPEC = BUILDER
            .comment("WebSocket Gateway URL for voice chat communication")
            .define("websocketStageUrl", "");

    private static final ModConfigSpec.ConfigValue<String> WEBSOCKET_API_KEY_SPEC = BUILDER
            .comment("API Key for WebSocket Gateway authentication")
            .define("websocketApiKey", "");

    private static final ModConfigSpec.ConfigValue<String> USER_POOL_ID_SPEC = BUILDER
            .comment("Cognito User Pool ID for authentication")
            .define("userPoolId", "");

    private static final ModConfigSpec.ConfigValue<String> USER_POOL_CLIENT_ID_SPEC = BUILDER
            .comment("Cognito User Pool Client ID for authentication")
            .define("userPoolClientId", "");

    // The actual config spec
    public static final ModConfigSpec SPEC = BUILDER.build();

    // These fields will hold the loaded values
    public static boolean enableVoiceChat;
    public static double defaultVolume;
    public static int maxVoiceDistance;
    public static String voiceGatewayUrl;
    public static String voiceGatewayApiKey;
    public static int reconnectionAttempts;
    public static int reconnectionDelay;
    public static String userPoolId;
    public static String userPoolClientId;

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
        if (event.getConfig().getSpec() == Config.SPEC) {
            VoiceChatMod.LOGGER.info("Loading VoiceChatMod configuration...");
            
            // Load basic configuration from TOML
            enableVoiceChat = ENABLE_VOICE_CHAT_SPEC.get();
            defaultVolume = DEFAULT_VOLUME_SPEC.get();
            maxVoiceDistance = MAX_VOICE_DISTANCE_SPEC.get();
            reconnectionAttempts = RECONNECTION_ATTEMPTS_SPEC.get();
            reconnectionDelay = RECONNECTION_DELAY_SPEC.get();

            // Load AWS configuration from TOML
            voiceGatewayUrl = WEBSOCKET_STAGE_URL_SPEC.get();
            voiceGatewayApiKey = WEBSOCKET_API_KEY_SPEC.get();
            userPoolId = USER_POOL_ID_SPEC.get();
            userPoolClientId = USER_POOL_CLIENT_ID_SPEC.get();

            // Validate configuration
            boolean configValid = true;
            if (voiceGatewayUrl.isEmpty()) {
                VoiceChatMod.LOGGER.error("WebSocket URL not configured!");
                configValid = false;
            }
            if (voiceGatewayApiKey.isEmpty()) {
                VoiceChatMod.LOGGER.error("API Key not configured!");
                configValid = false;
            }

            if (!configValid) {
                VoiceChatMod.LOGGER.error("Failed to load required configuration. Voice chat will be disabled.");
                enableVoiceChat = false;
            } else {
                VoiceChatMod.LOGGER.info("Voice Gateway configuration loaded successfully.");
            }
        } else {
            VoiceChatMod.LOGGER.debug("Ignoring config load event for non-matching spec: {}", event.getConfig().getFileName());
        }
    }
}
