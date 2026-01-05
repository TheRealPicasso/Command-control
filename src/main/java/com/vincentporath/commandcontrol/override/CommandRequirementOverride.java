package com.vincentporath.commandcontrol.override;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.vincentporath.commandcontrol.CommandControl;
import com.vincentporath.commandcontrol.config.CommandControlConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.util.function.Predicate;

/**
 * Overrides command requirements using reflection to allow non-OP players
 * to use commands based on their LuckPerms rank.
 * 
 * Inspired by Player Roles mod's approach:
 * https://github.com/NucleoidMC/player-roles
 */
public class CommandRequirementOverride {
    
    private static Field requirementField;
    private static boolean initialized = false;
    
    /**
     * Initialize reflection - get access to the requirement field
     */
    public static boolean initialize() {
        if (initialized) return true;
        
        try {
            requirementField = CommandNode.class.getDeclaredField("requirement");
            requirementField.setAccessible(true);
            initialized = true;
            CommandControl.LOGGER.info("[CommandControls] Reflection initialized successfully");
            return true;
        } catch (NoSuchFieldException e) {
            CommandControl.LOGGER.error("[CommandControls] Failed to find 'requirement' field in CommandNode", e);
            return false;
        }
    }
    
    /**
     * Apply our permission overrides to all commands in the dispatcher
     */
    public static void applyOverrides(CommandDispatcher<ServerCommandSource> dispatcher) {
        if (!initialize()) {
            CommandControl.LOGGER.error("[CommandControls] Cannot apply overrides - reflection not initialized");
            return;
        }
        
        RootCommandNode<ServerCommandSource> root = dispatcher.getRoot();
        int overrideCount = 0;
        
        for (CommandNode<ServerCommandSource> child : root.getChildren()) {
            if (overrideCommandNode(child, child.getName())) {
                overrideCount++;
            }
        }
        
        CommandControl.LOGGER.info("[CommandControls] Applied permission overrides to {} commands", overrideCount);
    }
    
    /**
     * Override a single command node's requirement
     */
    @SuppressWarnings("unchecked")
    private static boolean overrideCommandNode(CommandNode<ServerCommandSource> node, String commandPath) {
        try {
            // Get the original requirement
            Predicate<ServerCommandSource> originalRequirement = 
                    (Predicate<ServerCommandSource>) requirementField.get(node);
            
            String commandName = commandPath.split(" ")[0].toLowerCase();
            
            // Create new requirement that checks our config first
            Predicate<ServerCommandSource> newRequirement = source -> {
                // Check if this is a player
                if (source.getEntity() instanceof ServerPlayerEntity player) {
                    // If player is already OP level 4, use original check
                    if (source.hasPermissionLevel(4)) {
                        return originalRequirement.test(source);
                    }
                    
                    // Check if command is allowed for this player via our config
                    if (CommandControlConfig.isCommandAllowed(player, commandName)) {
                        // Command is allowed - return true regardless of original requirement
                        return true;
                    }
                }
                
                // Fall back to original requirement
                return originalRequirement.test(source);
            };
            
            // Set the new requirement
            requirementField.set(node, newRequirement);
            
            // Recursively apply to children (for subcommands)
            for (CommandNode<ServerCommandSource> child : node.getChildren()) {
                overrideCommandNode(child, commandPath + " " + child.getName());
            }
            
            return true;
            
        } catch (IllegalAccessException e) {
            CommandControl.LOGGER.error("[CommandControls] Failed to override requirement for {}", commandPath, e);
            return false;
        }
    }
}
