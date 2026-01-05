package com.vincentporath.commandcontrol.mixin;

import com.vincentporath.commandcontrol.CommandControl;
import net.minecraft.server.dedicated.command.DeOpCommand;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Mixin to detect when /deop command is executed
 */
@Mixin(DeOpCommand.class)
public class DeOpCommandMixin {

    @Inject(method = "deop", at = @At("RETURN"))
    private static void commandcontrol$onDeop(ServerCommandSource source, Collection<GameProfile> targets, CallbackInfoReturnable<Integer> cir) {
        // Trigger resync after /deop is executed
        CommandControl.triggerOpResync();
    }
}
