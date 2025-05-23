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
            .comment("The URL of the voice chat server (e.g., your AWS AppSync WebSocket endpoint).")
            .define("voiceServerUrl", "wss://example.appsync-realtime-api.us-east-1.amazonaws.com/graphql"); // Placeholder URL

    private static final ModConfigSpec.IntValue MAX_VOICE_DISTANCE_SPEC = BUILDER
            .comment("Maximum distance (in blocks) at which players can hear each other. Set to 0 for global chat (if server supports).")
            .defineInRange("maxVoiceDistance", 64, 0, 256); // Example range

    // The final configuration specification that NeoForge will use.
    // This MUST be registered in the main mod class (VoiceChatMod.java).
    static final ModConfigSpec SPEC = BUILDER.build();

    // --- Static fields for easy access to config values ---
    // These fields will be populated when the config is loaded or reloaded.
    // This provides a simpler way to access config values from other parts of the mod.
    public static boolean enableVoiceChat;
    public static double defaultVolume;
    public static String voiceServerUrl;
    public static int maxVoiceDistance;

    /**
     * This method is automatically called by NeoForge when a mod config file for this mod is loaded or reloaded.
     * It updates the static fields with the latest values from the configuration.
     *
     * @param event The ModConfigEvent containing details about the loaded config.
     */
    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Check if the loaded config is our common config.
        // This is important if you have multiple config files (e.g., client, server, common).
        if (event.getConfig().getSpec() == Config.SPEC) {
            VoiceChatMod.LOGGER.info("Loading VoiceChatMod common configuration...");
            enableVoiceChat = ENABLE_VOICE_CHAT_SPEC.get();
            defaultVolume = DEFAULT_VOLUME_SPEC.get();
            voiceServerUrl = VOICE_SERVER_URL_SPEC.get();
            maxVoiceDistance = MAX_VOICE_DISTANCE_SPEC.get();

            // Log loaded config values for verification (optional)
            VoiceChatMod.LOGGER.debug("Voice Chat Enabled: {}", enableVoiceChat);
            VoiceChatMod.LOGGER.debug("Default Volume: {}", defaultVolume);
            VoiceChatMod.LOGGER.debug("Voice Server URL: {}", voiceServerUrl);
            VoiceChatMod.LOGGER.debug("Max Voice Distance: {}", maxVoiceDistance);
        }
    }
}
