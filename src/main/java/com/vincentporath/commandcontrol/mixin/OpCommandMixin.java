package com.vincentporath.commandcontrol.mixin;

import com.vincentporath.commandcontrol.CommandControl;
import net.minecraft.server.dedicated.command.OpCommand;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Mixin to detect when /op command is executed
 */
@Mixin(OpCommand.class)
public class OpCommandMixin {

    @Inject(method = "op", at = @At("RETURN"))
    private static void commandcontrol$onOp(ServerCommandSource source, Collection<GameProfile> targets, CallbackInfoReturnable<Integer> cir) {
        // Trigger resync after /op is executed
        CommandControl.triggerOpResync();
    }
}
