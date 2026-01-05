package com.vincentporath.commandcontrol.mixin;

import com.vincentporath.commandcontrol.CommandControl;
import com.vincentporath.commandcontrol.config.CommandControlConfig;
import com.vincentporath.commandcontrol.util.CommandExecutionTracker;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to track the current command being executed.
 * 
 * Strategy: We track the command name in a ThreadLocal so that
 * ServerCommandSourceMixin can check if permission should be granted
 * during hasPermissionLevel() calls.
 */
@Mixin(CommandManager.class)
public abstract class CommandExecutionMixin {

    /**
     * Track the command before execution starts.
     * This allows ServerCommandSourceMixin.hasPermissionLevel to know which command is being checked.
     */
    @Inject(method = "executeWithPrefix", at = @At("HEAD"))
    private void commandcontrol$trackCommandStart(
            ServerCommandSource source, 
            String command, 
            CallbackInfoReturnable<Integer> cir
    ) {
        String rootCommand = extractRootCommand(command);
        
        // Track which command is being executed (for permission checks)
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            // Only track for non-OP players
            if (!source.getServer().getPlayerManager().isOperator(player.getGameProfile())) {
                // Check if this command is allowed for the player
                if (CommandControlConfig.isCommandAllowed(player, rootCommand)) {
                    CommandExecutionTracker.setCurrentCommand(rootCommand);
                    CommandControl.LOGGER.debug("[CommandControls] Tracking command '{}' for player {}", 
                            rootCommand, player.getName().getString());
                }
            }
        }
    }
    
    /**
     * Clear command tracking after execution completes.
     */
    @Inject(method = "executeWithPrefix", at = @At("RETURN"))
    private void commandcontrol$trackCommandEnd(
            ServerCommandSource source, 
            String command, 
            CallbackInfoReturnable<Integer> cir
    ) {
        CommandExecutionTracker.clearCurrentCommand();
    }
    
    /**
     * Extract the root command name from the full command string
     * e.g. "/tp player1 player2" -> "tp"
     */
    private String extractRootCommand(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        
        // Remove leading slash if present
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        
        // Get first word (the command name)
        int spaceIndex = command.indexOf(' ');
        if (spaceIndex > 0) {
            return command.substring(0, spaceIndex).toLowerCase();
        }
        
        return command.toLowerCase();
    }
}
