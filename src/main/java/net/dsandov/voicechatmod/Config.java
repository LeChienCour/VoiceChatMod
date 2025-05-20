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

    private static final ModConfigSpec.ConfigValue<String> VOICE_GATEWAY_URL_SPEC = BUILDER
            .comment("The WebSocket URL for the Voice Gateway (AWS API Gateway). Example: wss://<api-id>.execute-api.<region>.amazonaws.com/<stage>")
            .define("voiceGatewayUrl", "YOUR_WEBSOCKET_API_GATEWAY_URL_HERE");

    private static final ModConfigSpec.ConfigValue<String> VOICE_GATEWAY_API_KEY_SPEC = BUILDER
            .comment("Optional API key for AWS API Gateway authentication. Leave empty if not required.")
            .define("voiceGatewayApiKey", "");

    private static final ModConfigSpec.IntValue MAX_VOICE_DISTANCE_SPEC = BUILDER
            .comment("Maximum distance (in blocks) at which players can hear each other. Set to 0 for global chat (if server supports).")
            .defineInRange("maxVoiceDistance", 64, 0, 256);

    private static final ModConfigSpec.IntValue RECONNECTION_ATTEMPTS_SPEC = BUILDER
            .comment("Number of times to attempt reconnection to the voice gateway if connection is lost.")
            .defineInRange("reconnectionAttempts", 3, 0, 10);

    private static final ModConfigSpec.IntValue RECONNECTION_DELAY_SPEC = BUILDER
            .comment("Delay in seconds between reconnection attempts.")
            .defineInRange("reconnectionDelay", 5, 1, 30);

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
            voiceGatewayUrl = VOICE_GATEWAY_URL_SPEC.get();
            voiceGatewayApiKey = VOICE_GATEWAY_API_KEY_SPEC.get();
            reconnectionAttempts = RECONNECTION_ATTEMPTS_SPEC.get();
            reconnectionDelay = RECONNECTION_DELAY_SPEC.get();

            // Validate WebSocket Gateway configuration
            boolean configValid = true;
            if (voiceGatewayUrl == null || voiceGatewayUrl.equals("YOUR_WEBSOCKET_API_GATEWAY_URL_HERE")) {
                VoiceChatMod.LOGGER.error("Voice Gateway WebSocket URL is not configured in voicechatmod-common.toml!");
                configValid = false;
            }

            if (!configValid) {
                VoiceChatMod.LOGGER.error("Please configure the Voice Gateway settings in config/voicechatmod-common.toml!");
            } else {
                VoiceChatMod.LOGGER.info("Voice Gateway configuration loaded and validated successfully.");
            }
        } else {
            VoiceChatMod.LOGGER.debug("Ignoring config load event for non-matching spec: {}", event.getConfig().getFileName());
        }
    }
}
