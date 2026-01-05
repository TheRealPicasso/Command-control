package com.vincentporath.commandcontrol.mixin;

import com.vincentporath.commandcontrol.config.CommandControlConfig;
import com.vincentporath.commandcontrol.util.CommandExecutionTracker;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to override permission checks for commands that are allowed in the config.
 * This works for both vanilla AND mod commands that use standard Minecraft permission checks.
 */
@Mixin(ServerCommandSource.class)
public abstract class ServerCommandSourceMixin {

    @Shadow
    public abstract boolean isExecutedByPlayer();
    
    @Shadow
    public abstract ServerPlayerEntity getPlayer();

    /**
     * Override hasPermissionLevel to allow commands from config.
     * This is called when Minecraft/mods check if a player can execute a command.
     * 
     * Note: This also covers entity selectors (@a, @p, etc.) since they check hasPermissionLevel(2)
     */
    @Inject(method = "hasPermissionLevel", at = @At("HEAD"), cancellable = true)
    private void commandcontrol$overridePermissionCheck(int level, CallbackInfoReturnable<Boolean> cir) {
        // Only override for players
        if (!isExecutedByPlayer()) {
            return;
        }
        
        try {
            ServerPlayerEntity player = getPlayer();
            if (player == null) {
                return;
            }
            
            // Check OP level directly via the game profile to avoid recursion
            int opLevel = player.server.getPermissionLevel(player.getGameProfile());
            if (opLevel >= 4) {
                return; // OP players have full access
            }
            
            // Check if we have a tracked command and if it's allowed
            String currentCommand = CommandExecutionTracker.getCurrentCommand();
            if (currentCommand != null && !currentCommand.isEmpty()) {
                if (CommandControlConfig.isCommandAllowed(player, currentCommand)) {
                    cir.setReturnValue(true);
                }
            }
        } catch (Exception e) {
            // Silently fail - don't override permission
        }
    }
}
