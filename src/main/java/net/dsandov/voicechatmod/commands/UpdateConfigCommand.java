package net.dsandov.voicechatmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.dsandov.voicechatmod.util.ConfigUpdater;
import net.dsandov.voicechatmod.VoiceChatMod;

import java.nio.file.Path;
import java.nio.file.Paths;

public class UpdateConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vc")
            .then(Commands.literal("updateconfig")
                .requires(source -> source.hasPermission(2)) // Requires operator permission
                .executes(UpdateConfigCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        try {
            String configPath = Paths.get("config", "voicechatmod-common.toml").toString();
            ConfigUpdater.updateConfigFromSSM(configPath);
            
            context.getSource().sendSuccess(() -> 
                Component.literal("Successfully updated configuration from SSM parameters"), true);
            
            // Notify that a restart might be needed
            context.getSource().sendSuccess(() -> 
                Component.literal("Please restart the game for changes to take effect"), true);
                
            return 1;
        } catch (Exception e) {
            VoiceChatMod.LOGGER.error("Error executing update config command", e);
            context.getSource().sendFailure(
                Component.literal("Failed to update configuration: " + e.getMessage()));
            return 0;
        }
    }
} 