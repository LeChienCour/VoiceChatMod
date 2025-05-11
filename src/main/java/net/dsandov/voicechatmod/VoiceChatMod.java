package net.dsandov.voicechatmod;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

// Minecraft & NeoForge specific imports
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(VoiceChatMod.MOD_ID)
public class VoiceChatMod {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "voicechatmod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructor for the VoiceChatMod.
     * This is where you should register event listeners, configurations, etc.
     *
     * @param modEventBus The event bus for this mod, used to register lifecycle event handlers.
     * @param modContainer The container for this mod, providing mod-specific information and context.
     */
    public VoiceChatMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("{} constructor is loading...", MOD_ID);

        // Register the commonSetup method for FMLCommonSetupEvent.
        // This event is fired when it's time to perform setup tasks common to client and server.
        modEventBus.addListener(this::commonSetup);

        // Register the mod's configuration file.
        // This tells NeoForge to load our "voicechatmod-common.toml" file using the spec from Config.java
        LOGGER.info("Registering Common configuration for {}.", MOD_ID);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "voicechatmod-common.toml");

        // The ClientModEvents class with @EventBusSubscriber will be automatically registered by NeoForge.
        // If you had instance methods for client events, you'd register them like:
        // if (FMLEnvironment.dist == Dist.CLIENT) {
        //    modEventBus.addListener(this::clientSetupMethodInstance);
        // }
        // For static methods in an @EventBusSubscriber class, NeoForge handles it.

        LOGGER.info("{} has been initialized.", MOD_ID);
    }

    /**
     * This method is called during the FMLCommonSetupEvent.
     * Use this for setup tasks that are common to both client and server environments.
     *
     * @param event The FMLCommonSetupEvent instance.
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Executing commonSetup for {}.", MOD_ID);
        // This is a good place to initialize systems that depend on configurations being loaded
        // or that are common to both client and server.
        // For example, you might check Config.enableVoiceChat here.
        if (Config.enableVoiceChat) {
            LOGGER.info("Voice Chat is enabled via configuration. Further initialization can occur here.");
            // Initialize voice handlers, network components (not AWS SDK client yet, that's more specific).
        } else {
            LOGGER.info("Voice Chat is disabled via configuration.");
        }
    }

    /**
     * Example server starting event.
     * You might use this if your mod needs to perform actions when a dedicated server starts
     * or when a single-player world's integrated server starts.
     *
     * @param event The ServerStartingEvent instance.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // This method is registered on the NeoForge.EVENT_BUS automatically due to @SubscribeEvent
        // on an instance method if 'NeoForge.EVENT_BUS.register(this);' was called in constructor.
        // Or, if this class itself is registered on NeoForge.EVENT_BUS.
        // For now, this is a placeholder. If not needed, it can be removed.
        // For this to be called, this instance of VoiceChatMod would need to be registered to NeoForge.EVENT_BUS.
        // Typically, @SubscribeEvent on instance methods are for classes registered to an event bus.
        // Static methods in @EventBusSubscriber classes are auto-registered.
        // If you want this to fire, you might need `NeoForge.EVENT_BUS.register(this);` in the constructor.
        // However, for mod lifecycle events, the modEventBus is preferred.
        LOGGER.info("{}'s onServerStarting event fired. Server world name: {}", MOD_ID, event.getServer().getWorldData().getLevelName());
    }

    // Static inner class for handling client-specific events.
    // The @EventBusSubscriber annotation automatically registers static methods within this class
    // that are annotated with @SubscribeEvent to the MOD event bus if bus = Bus.MOD,
    // or the FORGE event bus if bus = Bus.FORGE.
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        /**
         * This method is called during the FMLClientSetupEvent.
         * Use this for setup tasks that are specific to the client environment,
         * such as registering key bindings, screen factories, or renderers.
         *
         * @param event The FMLClientSetupEvent instance.
         */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Executing clientSetup for {}.", MOD_ID);
            // Example: KeyBindingRegistry.registerKeyBinding(MY_KEY_BINDING);
            // We will add keybindings for voice chat (e.g., push-to-talk) here later.
        }
    }
}