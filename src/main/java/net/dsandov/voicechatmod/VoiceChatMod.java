package net.dsandov.voicechatmod;

import net.dsandov.voicechatmod.aws.AppSyncClientService;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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
import net.dsandov.voicechatmod.Keybinds;
import net.dsandov.voicechatmod.audio.AudioManager;
import net.dsandov.voicechatmod.audio.MicrophoneManager;

@Mod(VoiceChatMod.MOD_ID)
public class VoiceChatMod {
    public static final String MOD_ID = "voicechatmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VoiceChatMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("{} constructor is loading...", MOD_ID);

        // Register FML lifecycle events to the MOD event bus
        modEventBus.addListener(this::commonSetup);
        // ClientModEvents (inner class) handles its own MOD bus events via @EventBusSubscriber

        // Register game events that this class instance will handle on the FORGE event bus
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands); // For command registration
        NeoForge.EVENT_BUS.register(this); // For instance method @SubscribeEvents like onServerStarting

        // Manually register ClientForgeEvents class to the FORGE event bus for its static @SubscribeEvent methods.
        // This is done only on the client side as ClientTickEvent is client-only.
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(ClientForgeEvents.class);
            LOGGER.info("Registered ClientForgeEvents to the NeoForge EVENT_BUS for client-side game events.");
        }

        LOGGER.info("Registering Common configuration for {}.", MOD_ID);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "voicechatmod-common.toml");

        LOGGER.info("{} has been initialized.", MOD_ID);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Executing commonSetup for {}.", MOD_ID);
        if (Config.enableVoiceChat) {
            LOGGER.info("Voice Chat is enabled via configuration.");
        } else {
            LOGGER.info("Voice Chat is disabled via configuration.");
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // This method is called because 'this' instance (VoiceChatMod) is registered to NeoForge.EVENT_BUS
        LOGGER.info("{}'s onServerStarting event fired. Server world name: {}", MOD_ID, event.getServer().getWorldData().getLevelName());
    }

    // This method is an event handler because it's registered via addListener
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
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
                .then(Commands.literal("playloopback")
                        .executes(context -> {
                            ClientModEvents.executeClientSideTask(() -> {
                                if (ClientModEvents.audioManager != null && ClientModEvents.audioManager.isInitialized()) {
                                    ClientModEvents.playLoopbackAudio();
                                    // Feedback can be part of playLoopbackAudio or here
                                    context.getSource().sendSuccess(() -> Component.literal("Attempting to play accumulated loopback audio."), false);
                                } else {
                                    context.getSource().sendFailure(Component.literal("Audio Manager not ready for loopback playback."));
                                }
                            });
                            return 1; // Return 1 for success
                        })
                )
        );
        LOGGER.info("Registered /vc commands for VoiceChatMod.");
    }

    /**
     * Inner class for handling client-side events that belong to the MOD event bus.
     * This class uses @EventBusSubscriber to automatically register its static @SubscribeEvent methods to the MOD bus.
     */
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        public static MicrophoneManager microphoneManager;
        public static AudioManager audioManager;
        public static AppSyncClientService appSyncClientService;
        private static ByteArrayOutputStream loopbackAudioBuffer = new ByteArrayOutputStream();
        private static final int MAX_LOOPBACK_BUFFER_SIZE_SECONDS = 5;
        private static final int BYTES_PER_SECOND = 16000 * 2; // Assuming 16kHz, 16-bit mono

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Executing clientSetup for {}.", MOD_ID);

            MicrophoneManager.listAvailableMicrophones(); // For debugging
            microphoneManager = new MicrophoneManager();
            if (microphoneManager.initialize()) {
                LOGGER.info("MicrophoneManager initialized successfully.");
            } else {
                LOGGER.error("MicrophoneManager failed to initialize.");
            }

            audioManager = new AudioManager();
            if (audioManager.initialize()) {
                LOGGER.info("AudioManager initialized successfully.");
            } else {
                LOGGER.error("AudioManager failed to initialize.");
            }
            VoiceChatMod.LOGGER.info("Attempting to initialize AppSyncClientService...");
            try {
                appSyncClientService = new AppSyncClientService();
                VoiceChatMod.LOGGER.info("AppSyncClientService instance after new: " + (appSyncClientService == null ? "IS NULL" : "IS NOT NULL")); // NUEVO LOG DE DIAGNÓSTICO
                if (appSyncClientService == null) {
                    VoiceChatMod.LOGGER.error("<<<<< AppSyncClientService IS NULL immediately after instantiation! This should not happen. >>>>>");
                    return;
                }
                VoiceChatMod.LOGGER.info("AppSyncClientService instance CREATED and NOT NULL. Queueing connect task...");
                final AppSyncClientService serviceToUseInLambda = appSyncClientService; // Capturar la referencia localmente
                executeClientSideTask(() -> {
                    VoiceChatMod.LOGGER.info(">>> executeClientSideTask for AppSync: Task IS RUNNING!");
                    if (serviceToUseInLambda != null) {
                        serviceToUseInLambda.initializeAndConnect().thenAcceptAsync(success -> {
                        }, net.minecraft.client.Minecraft.getInstance()::execute);
                    } else {
                        VoiceChatMod.LOGGER.error(">>> executeClientSideTask for AppSync: serviceToUseInLambda was NULL!");
                    }
                });
            } catch (Throwable t) {
                VoiceChatMod.LOGGER.error(">>> CRITICAL ERROR during AppSyncClientService instantiation or task queuing in onClientSetup!", t);
            }
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            Keybinds.initializeKeybindings(); // Initialize our KeyMapping objects
            event.register(Keybinds.PUSH_TO_TALK_KEY); // Register the PTT key
            VoiceChatMod.LOGGER.info("Registered keybindings for {}.", MOD_ID);
        }

        // --- Helper methods for client-side tasks and loopback buffer ---
        public static void executeClientSideTask(Runnable task) {
            // Ensure task runs on the main client thread
            if (FMLEnvironment.dist.isClient()) { // Should always be true if called from client events
                net.minecraft.client.Minecraft.getInstance().execute(task);
            } else {
                // This case should ideally not be reached if logic is structured correctly
                VoiceChatMod.LOGGER.warn("Attempted to execute client-side task from a non-client environment. Task: " + task.toString());
            }
        }

        public static void clearLoopbackBuffer() {
            loopbackAudioBuffer.reset();
            VoiceChatMod.LOGGER.info("Loopback audio buffer cleared.");
        }

        public static void appendToLoopbackBuffer(byte[] audioData, int length) {
            if (audioData != null && length > 0) {
                if (loopbackAudioBuffer.size() < MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND) {
                    loopbackAudioBuffer.write(audioData, 0, length);
                } else if (loopbackAudioBuffer.size() >= MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND &&
                        loopbackAudioBuffer.size() < MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND + length) {
                    VoiceChatMod.LOGGER.warn("Loopback buffer limit reached ({} seconds). Further audio for this loopback recording will be ignored.", MAX_LOOPBACK_BUFFER_SIZE_SECONDS);
                    loopbackAudioBuffer.write(audioData, 0, length); // Write the last bit to fill
                }
                // If buffer is full, new data is ignored silently after the warning.
            }
        }

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

    /**
     * Inner class for handling client-side events that belong to the FORGE (NeoForge) event bus.
     * This class does NOT use @EventBusSubscriber annotation because its class is manually registered
     * to the NeoForge.EVENT_BUS in the main mod constructor (for client side only).
     * Its static methods annotated with @SubscribeEvent will be picked up.
     */
    // NO @EventBusSubscriber annotation here
    public static class ClientForgeEvents {
        // State variable to track if PTT key was pressed in the previous tick
        private static boolean isPTTKeyPressed = false;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre event) {
            // Ensure microphoneManager is initialized (it's in ClientModEvents)
            // and we are in a playable state (e.g., player exists, not in a menu where PTT might be irrelevant)
            if (ClientModEvents.microphoneManager == null || net.minecraft.client.Minecraft.getInstance().player == null || net.minecraft.client.Minecraft.getInstance().screen != null) {
                // If PTT was active but we are no longer in a state to use it (e.g., opened a menu, left world)
                // ensure microphone is stopped.
                if (isPTTKeyPressed && ClientModEvents.microphoneManager != null && ClientModEvents.microphoneManager.isCapturing()) {
                    ClientModEvents.microphoneManager.stopCapture();
                    isPTTKeyPressed = false; // Reset PTT state
                    VoiceChatMod.LOGGER.debug("PTT key was active, but context changed (e.g., menu open/not in world), stopping capture.");
                }
                return;
            }

            // Check if the PTT key is currently being pressed down
            boolean pttCurrentlyPressed = Keybinds.PUSH_TO_TALK_KEY.isDown();

            if (pttCurrentlyPressed && !isPTTKeyPressed) {
                // PTT key was just pressed
                VoiceChatMod.LOGGER.debug("PTT key pressed - Starting microphone capture.");
                // Access microphoneManager via ClientModEvents class name
                ClientModEvents.microphoneManager.startCapture();
                isPTTKeyPressed = true;
            } else if (!pttCurrentlyPressed && isPTTKeyPressed) {
                // PTT key was just released
                VoiceChatMod.LOGGER.debug("PTT key released - Stopping microphone capture.");
                ClientModEvents.microphoneManager.stopCapture();
                isPTTKeyPressed = false;
            }
        }
    }
}