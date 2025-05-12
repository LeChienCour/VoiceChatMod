package net.dsandov.voicechatmod;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
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
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.ByteArrayOutputStream;

import net.dsandov.voicechatmod.audio.MicrophoneManager;
import net.dsandov.voicechatmod.audio.AudioManager;

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

        // Register the command registration event listener
        // This uses the NeoForge.EVENT_BUS because command registration is a game-level event, not mod-specific lifecycle.
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

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
     * This method is called when NeoForge fires the RegisterCommandsEvent.
     * We use this to register our custom commands.
     * @param event The RegisterCommandsEvent instance.
     */
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Build the command /vc
        dispatcher.register(Commands.literal("vc")
                .then(Commands.literal("micstart")
                        .executes(context -> {
                            ClientModEvents.executeClientSideTask(() -> {
                                if (ClientModEvents.microphoneManager != null) {
                                    // Clear previous loopback audio before starting a new "recording"
                                    ClientModEvents.clearLoopbackBuffer();
                                    ClientModEvents.microphoneManager.startCapture();
                                    context.getSource().sendSuccess(() -> Component.literal("Microphone capture started. Loopback buffer cleared."), true);
                                } else {
                                    context.getSource().sendFailure(Component.literal("MicrophoneManager not initialized on client."));
                                }
                            });
                            return 1;
                        })
                )
                .then(Commands.literal("micstop")
                        .executes(context -> {
                            ClientModEvents.executeClientSideTask(() -> {
                                if (ClientModEvents.microphoneManager != null) {
                                    ClientModEvents.microphoneManager.stopCapture();
                                    context.getSource().sendSuccess(() -> Component.literal("Microphone capture stopped."), true);
                                } else {
                                    context.getSource().sendFailure(Component.literal("MicrophoneManager not initialized on client."));
                                }
                            });
                            return 1; // Return 1 for success
                        })
                )
                .then(Commands.literal("playloopback") // Renombrado de testloopback
                        .executes(context -> {
                            ClientModEvents.executeClientSideTask(() -> {
                                if (ClientModEvents.microphoneManager != null && ClientModEvents.audioManager != null && ClientModEvents.audioManager.isInitialized()) {
                                    ClientModEvents.playLoopbackAudio(); // Llama al método que reproduce el buffer acumulado
                                    // El feedback al jugador ya está dentro de playLoopbackAudio o se puede añadir aquí
                                    context.getSource().sendSuccess(() -> Component.literal("Attempting to play accumulated loopback audio."), false);
                                } else {
                                    context.getSource().sendFailure(Component.literal("Mic or Audio Manager not ready for loopback."));
                                }
                            });
                            return 1;
                        })
                )
        );
        LOGGER.info("Registered /vc commands for VoiceChatMod.");
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

        // Make MicrophoneManager instance accessible if needed elsewhere, or keep it local
        private static MicrophoneManager microphoneManager;
        public static AudioManager audioManager;
        private static ByteArrayOutputStream loopbackAudioBuffer = new ByteArrayOutputStream();
        private static final int MAX_LOOPBACK_BUFFER_SIZE_SECONDS = 5; // Max seconds to record for loopback
        private static final int BYTES_PER_SECOND = 16000 * 2; // 16kHz, 16-bit mono (2 bytes per sample)


        public static MicrophoneManager getMicrophoneManager() {
            return microphoneManager;
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Executing clientSetup for {}.", MOD_ID);

            // List available microphones for debugging (optional, but helpful first time)
            MicrophoneManager.listAvailableMicrophones();

            microphoneManager = new MicrophoneManager();
            if (microphoneManager.initialize()) { // Initialize attempts to get the line
                LOGGER.info("MicrophoneManager initialized successfully during client setup.");
                // For initial testing, you could try starting capture here,
                // but normally this would be tied to a keybind (Push-to-Talk).
                // microphoneManager.startCapture();
            } else {
                LOGGER.error("MicrophoneManager failed to initialize during client setup.");
            }

            audioManager = new AudioManager();
            if (audioManager.initialize()) {
                LOGGER.info("AudioManager initialized successfully.");
            } else {
                LOGGER.error("AudioManager failed to initialize.");
            }

            // Example: To test stopping when the game closes (not ideal for PTT, but for basic test)
            // You might also want to ensure it's stopped if the client disconnects from a world
            // Minecraft.getInstance().add रूपरेखा( (client, tick) -> { /* do nothing on tick */ }, () -> {
            // if (microphoneManager != null && microphoneManager.isCapturing()) {
            // microphoneManager.stopCapture();
            // }
            // });
            // A better place to stop resources is when the game is shutting down or player leaves server.
            // For now, manual or PTT will be better.
        }

        /**
         * Helper method to ensure a task runs on the client thread.
         *
         * @param task The task to execute.
         */
        public static void executeClientSideTask(Runnable task) {
            // Minecraft.getInstance() is client-side only.
            // This check helps prevent calling it from a dedicated server environment,
            // though these /vc commands are more for local testing.
            if (FMLEnvironment.dist == Dist.CLIENT) {
                net.minecraft.client.Minecraft.getInstance().execute(task);
            } else {
                // If somehow called on a server, log an error or do nothing.
                // For these specific commands, they are intended for a client environment.
                VoiceChatMod.LOGGER.warn("Attempted to execute client-side task from a non-client environment.");
            }
        }

        /**
         * Clears the loopback audio buffer to start a new recording.
         * Should be called when mic capture for loopback starts.
         */
        public static void clearLoopbackBuffer() {
            loopbackAudioBuffer.reset(); // Clears the buffer
            VoiceChatMod.LOGGER.info("Loopback audio buffer cleared.");
        }

        /**
         * Appends captured audio data to the loopback buffer.
         *
         * @param audioData The raw audio data packet.
         * @param length    The number of valid bytes in audioData.
         */
        public static void appendToLoopbackBuffer(byte[] audioData, int length) {
            if (audioData != null && length > 0) {
                // Optional: Limit the size of the loopback buffer to avoid using too much memory
                if (loopbackAudioBuffer.size() < MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND) {
                    loopbackAudioBuffer.write(audioData, 0, length);
                } else if (loopbackAudioBuffer.size() >= MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND &&
                        loopbackAudioBuffer.size() < MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND + length) {
                    // Log only once when limit is reached
                    VoiceChatMod.LOGGER.warn("Loopback buffer limit reached ({} seconds). Further audio for this loopback recording will be ignored.", MAX_LOOPBACK_BUFFER_SIZE_SECONDS);
                    // Still write this last packet to fill it up if it was slightly under
                    loopbackAudioBuffer.write(audioData, 0, length);
                }
            }
        }

        /**
         * Plays the audio accumulated in the loopback buffer.
         */
        public static void playLoopbackAudio() {
            if (audioManager != null && audioManager.isInitialized()) {
                byte[] audioToPlay = loopbackAudioBuffer.toByteArray();
                if (audioToPlay.length > 0) {
                    VoiceChatMod.LOGGER.info("Attempting to play {} bytes of accumulated loopback audio.", audioToPlay.length);
                    audioManager.playAudio(audioToPlay, 0, audioToPlay.length);
                } else {
                    VoiceChatMod.LOGGER.warn("Loopback audio buffer is empty. Nothing to play.");
                }
            } else {
                VoiceChatMod.LOGGER.warn("AudioManager not ready for loopback playback.");
            }
        }
    }
}