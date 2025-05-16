package net.dsandov.voicechatmod;

import com.mojang.blaze3d.platform.InputConstants; // Required for key constants like GLFW.GLFW_KEY_V
import net.minecraft.client.KeyMapping; // The class for keybindings
import net.neoforged.neoforge.client.settings.KeyConflictContext; // For specifying when the keybind is active
import org.lwjgl.glfw.GLFW; // For actual key codes like GLFW_KEY_V

/**
 * Class to hold and manage custom keybindings for the mod.
 */
public class Keybinds {

    // Define a category for our mod's keybindings in the Minecraft controls menu
    public static final String KEY_CATEGORY_VOICECHATMOD = "key.category." + VoiceChatMod.MOD_ID;

    // Define the Push-to-Talk keybinding
    public static KeyMapping PUSH_TO_TALK_KEY;

    /**
     * Initializes and defines all keybindings for the mod.
     * This method should be called to populate the KeyMapping objects before they are registered.
     * We will call this from the RegisterKeyMappingsEvent handler.
     */
    public static void initializeKeybindings() {
        PUSH_TO_TALK_KEY = new KeyMapping(
                "key." + VoiceChatMod.MOD_ID + ".push_to_talk", // Translation key for the keybinding's name
                KeyConflictContext.IN_GAME, // Context: When is this keybind active? IN_GAME is common.
                InputConstants.Type.KEYSYM, // Type of input: KEYSYM for keyboard keys, MOUSE for mouse buttons
                GLFW.GLFW_KEY_V,            // Default key: 'V'. GLFW provides key codes.
                KEY_CATEGORY_VOICECHATMOD   // Category in the controls menu
        );
        // You can define more keybindings here if needed in the future
        // e.g., Mute Self, Deafen, Open Config GUI, etc.
    }

    // Note: Registration of these KeyMapping objects will be handled by an event listener.
}