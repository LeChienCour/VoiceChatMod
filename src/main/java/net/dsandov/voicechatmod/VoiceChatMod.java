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
import java.util.Base64;

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
                            return 1;
                        })
                )
                .then(Commands.literal("playloopback")
                        .executes(context -> {
                            ClientModEvents.executeClientSideTask(() -> {
                                if (ClientModEvents.audioManager != null && ClientModEvents.audioManager.isInitialized()) {
                                    ClientModEvents.playLoopbackAudio();
                                    context.getSource().sendSuccess(() -> Component.literal("Attempting to play accumulated loopback audio."), false);
                                } else {
                                    context.getSource().sendFailure(Component.literal("Audio Manager not ready for loopback playback."));
                                }
                            });
                            return 1;
                        })
                )
                .then(Commands.literal("echo")
                        .then(Commands.literal("on")
                            .executes(context -> {
                                ClientModEvents.executeClientSideTask(() -> {
                                    ClientModEvents.setEchoEnabled(true);
                                    context.getSource().sendSuccess(() -> Component.literal("Voice chat echo mode ENABLED - You will hear your own voice."), false);
                                });
                                return 1;
                            })
                        )
                        .then(Commands.literal("off")
                            .executes(context -> {
                                ClientModEvents.executeClientSideTask(() -> {
                                    ClientModEvents.setEchoEnabled(false);
                                    context.getSource().sendSuccess(() -> Component.literal("Voice chat echo mode DISABLED - You will not hear your own voice."), false);
                                });
                                return 1;
                            })
                        )
                        .then(Commands.literal("status")
                            .executes(context -> {
                                ClientModEvents.executeClientSideTask(() -> {
                                    boolean status = ClientModEvents.isEchoEnabled();
                                    context.getSource().sendSuccess(() -> 
                                        Component.literal("Voice chat echo mode is currently: " + 
                                            (status ? "ENABLED (hearing own voice)" : "DISABLED (not hearing own voice)")), false);
                                });
                                return 1;
                            })
                        )
                )
                .then(Commands.literal("voicechat")
                    .then(Commands.literal("test")
                        .executes(context -> {
                            if (ClientModEvents.appSyncClientService != null && ClientModEvents.appSyncClientService.isInitialized()) {
                                context.getSource().sendSuccess(() -> Component.literal("Running AppSync connection test..."), false);
                                boolean sent = ClientModEvents.appSyncClientService.sendTestMessage("test");
                                if (sent) {
                                    context.getSource().sendSuccess(() -> Component.literal("✓ AppSync test completed successfully. Check logs for details."), false);
                                } else {
                                    context.getSource().sendFailure(Component.literal("✗ AppSync test failed. Check logs for details."));
                                }
                            } else {
                                context.getSource().sendFailure(Component.literal("✗ AppSync service not initialized"));
                            }
                            return 1;
                        }))
                )
        );
        LOGGER.info("Registered /vc commands for VoiceChatMod.");
    }

    public static String getCurrentPlayerName() {
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            return net.minecraft.client.Minecraft.getInstance().player.getName().getString();
        }
        return "";
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
        private static boolean echoEnabled = false; // New flag for echo mode
        private static boolean isAppSyncInitializing = false;
        private static int appSyncInitRetries = 0;
        private static final int MAX_INIT_RETRIES = 3;

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Executing clientSetup for {}.", MOD_ID);

            // Initialize audio components first
            initializeAudioComponents();

            // Delay AppSync initialization until the game is fully loaded
            event.enqueueWork(() -> {
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    // Wait a bit longer for configs to be fully loaded
                    try {
                        Thread.sleep(5000);
                        initializeAppSyncIfNeeded();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            });
        }

        private static void initializeAudioComponents() {
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
        }

        private static void initializeAppSyncIfNeeded() {
            // Check if initialization is already in progress
            if (isAppSyncInitializing) {
                LOGGER.debug("AppSync initialization already in progress, skipping...");
                return;
            }

            // Check if already initialized successfully
            if (appSyncClientService != null && appSyncClientService.isInitialized()) {
                LOGGER.debug("AppSync already initialized successfully, skipping...");
                return;
            }

            // Check if we've exceeded retry attempts
            if (appSyncInitRetries >= MAX_INIT_RETRIES) {
                LOGGER.warn("Maximum AppSync initialization retries reached ({}). Will not attempt further retries.", MAX_INIT_RETRIES);
                return;
            }

            // Validate configuration
            if (!isAppSyncConfigValid()) {
                LOGGER.warn("AppSync configuration is not valid, skipping initialization.");
                return;
            }

            // Set initialization flag
            isAppSyncInitializing = true;

            try {
                LOGGER.info("Initializing AppSync service (attempt {}/{})...", appSyncInitRetries + 1, MAX_INIT_RETRIES);
                appSyncClientService = new AppSyncClientService();
                
                appSyncClientService.initializeAndConnect()
                    .thenAcceptAsync(success -> {
                        isAppSyncInitializing = false;
                        if (success) {
                            LOGGER.info("AppSync service initialized and connected successfully!");
                            appSyncInitRetries = 0; // Reset retry counter on success
                        } else {
                            LOGGER.error("Failed to initialize AppSync service.");
                            handleInitializationFailure();
                        }
                    }, net.minecraft.client.Minecraft.getInstance()::execute)
                    .exceptionally(throwable -> {
                        isAppSyncInitializing = false;
                        LOGGER.error("Critical error during AppSync service initialization!", throwable);
                        handleInitializationFailure();
                        return null;
                    });
            } catch (Throwable t) {
                isAppSyncInitializing = false;
                LOGGER.error("Critical error during AppSync service initialization!", t);
                handleInitializationFailure();
            }
        }

        private static boolean isAppSyncConfigValid() {
            return Config.appSyncApiKey != null 
                && !Config.appSyncApiKey.equals("YOUR_APPSYNC_API_KEY_HERE")
                && Config.voiceServerUrl != null 
                && !Config.voiceServerUrl.equals("YOUR_APPSYNC_GRAPHQL_API_URL_HERE");
        }

        private static void handleInitializationFailure() {
            appSyncInitRetries++;
            if (appSyncInitRetries < MAX_INIT_RETRIES) {
                // Schedule next retry with exponential backoff
                long delayMs = Math.min(1000 * (long)Math.pow(2, appSyncInitRetries), 30000); // Max 30 seconds
                LOGGER.info("Scheduling AppSync initialization retry in {} ms...", delayMs);
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    try {
                        Thread.sleep(delayMs);
                        initializeAppSyncIfNeeded();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } else {
                LOGGER.error("AppSync initialization failed after {} attempts. Manual intervention may be required.", MAX_INIT_RETRIES);
            }
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            Keybinds.initializeKeybindings();
            event.register(Keybinds.PUSH_TO_TALK_KEY);
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
                VoiceChatMod.LOGGER.debug("Adding {} bytes to LOCAL loopback buffer", length);
                if (loopbackAudioBuffer.size() < MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND) {
                    loopbackAudioBuffer.write(audioData, 0, length);
                } else if (loopbackAudioBuffer.size() >= MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND &&
                        loopbackAudioBuffer.size() < MAX_LOOPBACK_BUFFER_SIZE_SECONDS * BYTES_PER_SECOND + length) {
                    VoiceChatMod.LOGGER.warn("LOCAL loopback buffer limit reached ({} seconds). Further audio for this loopback recording will be ignored.", MAX_LOOPBACK_BUFFER_SIZE_SECONDS);
                    loopbackAudioBuffer.write(audioData, 0, length); // Write the last bit to fill
                }
                // If buffer is full, new data is ignored silently after the warning.
            }
        }

        public static void playLoopbackAudio() {
            if (audioManager != null && audioManager.isInitialized()) {
                byte[] audioToPlay = loopbackAudioBuffer.toByteArray();
                if (audioToPlay.length > 0) {
                    VoiceChatMod.LOGGER.info("Playing {} bytes from LOCAL loopback buffer", audioToPlay.length);
                    audioManager.playAudio(audioToPlay, 0, audioToPlay.length);
                } else {
                    VoiceChatMod.LOGGER.warn("LOCAL loopback buffer is empty. Nothing to play.");
                }
            } else {
                VoiceChatMod.LOGGER.warn("AudioManager not ready for loopback playback.");
            }
        }
        public static void processReceivedAudio(String encodedAudioData, String author) {
            if (audioManager != null && audioManager.isInitialized()) {
                try {
                    // Only process if it's from another player OR if echo is enabled
                    if (!author.equals(getCurrentPlayerName()) || echoEnabled) {
                        // Decode the Base64 audio data
                        byte[] audioData = Base64.getDecoder().decode(encodedAudioData);
                        
                        VoiceChatMod.LOGGER.debug("Processing {} bytes of audio data from {}{}", 
                            audioData.length, author, 
                            author.equals(getCurrentPlayerName()) ? " (ECHO)" : "");
                        
                        // Play the audio through the AudioManager
                        audioManager.playAudio(audioData, 0, audioData.length);
                    }
                } catch (IllegalArgumentException e) {
                    VoiceChatMod.LOGGER.error("Error decoding audio data from {}: {}", author, e.getMessage());
                } catch (Exception e) {
                    VoiceChatMod.LOGGER.error("Error processing audio from {}", author, e);
                }
            } else {
                VoiceChatMod.LOGGER.warn("Cannot process received audio - AudioManager not initialized");
            }
        }

        public static boolean isEchoEnabled() {
            return echoEnabled;
        }

        public static void setEchoEnabled(boolean enabled) {
            echoEnabled = enabled;
            VoiceChatMod.LOGGER.info("Voice chat echo mode: {}", enabled ? "ENABLED" : "DISABLED");
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