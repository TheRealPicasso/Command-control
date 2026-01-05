package com.vincentporath.commandcontrol.mixin.client;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.vincentporath.commandcontrol.client.CommandControlClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Only filters top-level command names, not arguments like player names
 */
@Mixin(ChatInputSuggestor.class)
public class CommandSuggestorMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("commandcontrol-suggestor");

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;
    
    @Shadow
    private TextFieldWidget textField;

    /**
     * Filter the suggestions after they are generated but before displayed
     * The method is named "show" in yarn mappings
     */
    @Inject(method = "show", at = @At("HEAD"))
    private void commandcontrol$filterSuggestions(boolean narrateFirstSuggestion, CallbackInfo ci) {
        LOGGER.info("[CommandControls] show() method called - mixin is working!");
        
        // Only filter if we've received sync from a CommandControl-enabled server
        if (!CommandControlClient.isSyncReceived()) {
            LOGGER.info("[CommandControls] No sync received, not filtering");
            return;
        }

        if (this.pendingSuggestions == null || this.textField == null) {
            return;
        }
        
        // Get current input text
        String input = this.textField.getText();
        
        // Only filter if this is a command (starts with /) and we're suggesting the command name
        // If there's already a space after the command, we're suggesting arguments - don't filter those
        if (!input.startsWith("/")) {
            return; // Not a command, don't filter
        }
        
        // Check if we're past the command name (there's a space in the input)
        String afterSlash = input.substring(1);
        if (afterSlash.contains(" ")) {
            return; // We're suggesting arguments (like player names), don't filter
        }
        
        LOGGER.info("[CommandControls] Filtering command suggestions for input: {}", input);

        // Replace the pending suggestions with filtered ones (only for command names)
        this.pendingSuggestions = this.pendingSuggestions.thenApply(suggestions -> {
            if (suggestions == null || suggestions.isEmpty()) {
                return suggestions;
            }
            
            int originalCount = suggestions.getList().size();
            List<Suggestion> filtered = new ArrayList<>();

            for (Suggestion suggestion : suggestions.getList()) {
                String text = suggestion.getText();

                // Extract command name (handle both with and without /)
                String commandName = text;
                if (commandName.startsWith("/")) {
                    commandName = commandName.substring(1);
                }

                // Handle namespaced commands like "minecraft:help"
                int colonIndex = commandName.indexOf(':');
                if (colonIndex > 0) {
                    commandName = commandName.substring(colonIndex + 1);
                }

                // Check if this command is allowed
                if (CommandControlClient.shouldShowCommand(commandName)) {
                    filtered.add(suggestion);
                }
            }
            
            LOGGER.info("[CommandControls] Filtered {}/{} suggestions", filtered.size(), originalCount);

            return new Suggestions(suggestions.getRange(), filtered);
        });
    }
}
