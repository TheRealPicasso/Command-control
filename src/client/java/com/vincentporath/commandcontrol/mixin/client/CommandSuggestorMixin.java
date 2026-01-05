package com.vincentporath.commandcontrol.mixin.client;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.vincentporath.commandcontrol.client.CommandControlClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Mixin to filter command suggestions in the chat input
 * This catches ALL command suggestions including client-side mod commands
 */
@Mixin(ChatInputSuggestor.class)
public class CommandSuggestorMixin {

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    /**
     * Filter the suggestions after they are generated but before displayed
     * The method is named "show" in yarn mappings
     */
    @Inject(method = "show", at = @At("HEAD"))
    private void commandcontrol$filterSuggestions(boolean narrateFirstSuggestion, CallbackInfo ci) {
        // Only filter if we've received sync from a CommandControl-enabled server
        if (!CommandControlClient.isSyncReceived()) {
            return;
        }

        if (this.pendingSuggestions == null) {
            return;
        }

        // Replace the pending suggestions with filtered ones
        this.pendingSuggestions = this.pendingSuggestions.thenApply(suggestions -> {
            if (suggestions == null || suggestions.isEmpty()) {
                return suggestions;
            }

            List<Suggestion> filtered = new ArrayList<>();

            for (Suggestion suggestion : suggestions.getList()) {
                String text = suggestion.getText();

                // Extract command name (handle both with and without /)
                String commandName = text;
                if (commandName.startsWith("/")) {
                    commandName = commandName.substring(1);
                }

                // Get base command (before any space, colon, or subcommand)
                int spaceIndex = commandName.indexOf(' ');
                int colonIndex = commandName.indexOf(':');
                
                if (colonIndex > 0 && (spaceIndex < 0 || colonIndex < spaceIndex)) {
                    // Handle namespaced commands like "minecraft:help"
                    commandName = commandName.substring(colonIndex + 1);
                }
                
                if (spaceIndex > 0) {
                    commandName = commandName.substring(0, spaceIndex);
                }

                // Check if this command is allowed
                if (CommandControlClient.shouldShowCommand(commandName)) {
                    filtered.add(suggestion);
                }
            }

            return new Suggestions(suggestions.getRange(), filtered);
        });
    }
}
