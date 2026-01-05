package com.vincentporath.commandcontrol.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.vincentporath.commandcontrol.CommandControl;
import com.vincentporath.commandcontrol.config.CommandControlConfig;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Mixin to handle command suggestions with elevated permissions.
 * This ensures that allowed commands show proper argument suggestions.
 */
@Mixin(ServerPlayNetworkHandler.class)
public class CommandSuggestionsMixin {

    @Shadow public ServerPlayerEntity player;

    /**
     * Intercept suggestion requests and provide suggestions with elevated permissions.
     */
    @Inject(method = "onRequestCommandCompletions", at = @At("HEAD"), cancellable = true)
    private void commandcontrol$handleSuggestionsWithElevatedPermissions(
            RequestCommandCompletionsC2SPacket packet,
            CallbackInfo ci
    ) {
        // Skip if player is already OP
        if (player.getServer().getPlayerManager().isOperator(player.getGameProfile())) {
            return;
        }
        
        String command = packet.getPartialCommand();
        String rootCommand = extractRootCommand(command);
        
        // Check if the command is allowed for this player
        if (CommandControlConfig.isCommandAllowed(player, rootCommand)) {
            CommandControl.LOGGER.debug("[CommandControls] Providing elevated suggestions for {} -> /{}", 
                player.getName().getString(), rootCommand);
            
            // Create elevated source
            ServerCommandSource elevatedSource = player.getCommandSource().withLevel(4);
            
            // Get the command manager
            CommandManager commandManager = player.getServer().getCommandManager();
            CommandDispatcher<ServerCommandSource> dispatcher = commandManager.getDispatcher();
            
            // Parse with elevated source
            StringReader reader = new StringReader(command);
            if (reader.canRead() && reader.peek() == '/') {
                reader.skip();
            }
            
            var parseResults = dispatcher.parse(reader, elevatedSource);
            
            // Get suggestions
            CompletableFuture<Suggestions> future = dispatcher.getCompletionSuggestions(parseResults);
            
            int completionId = packet.getCompletionId();
            future.thenAccept(suggestions -> {
                player.networkHandler.sendPacket(new CommandSuggestionsS2CPacket(completionId, suggestions));
            });
            
            ci.cancel();
        }
    }
    
    /**
     * Extract the root command name from the full command string
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
