package com.vincentporath.commandcontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.vincentporath.commandcontrol.CommandControl;
import com.vincentporath.commandcontrol.config.CommandControlConfig;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Commands for CommandControl administration
 */
public class CommandControlCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("commandcontrols")
                .requires(source -> source.hasPermissionLevel(4)) // Requires OP level 4
                .then(CommandManager.literal("reload")
                    .executes(CommandControlCommand::executeReload))
                .then(CommandManager.literal("resync")
                    .executes(CommandControlCommand::executeResync))
                .then(CommandManager.literal("luckperms-setup")
                    .executes(CommandControlCommand::executeLuckPermsSetup))
                .then(CommandManager.literal("status")
                    .executes(CommandControlCommand::executeStatus))
                .executes(CommandControlCommand::executeHelp)
        );
    }
    
    private static int executeHelp(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        source.sendMessage(Text.literal("§6=== CommandControls Help ==="));
        source.sendMessage(Text.literal("§e/commandcontrols reload §7- Reload config"));
        source.sendMessage(Text.literal("§e/commandcontrols resync §7- Resync commands for all players"));
        source.sendMessage(Text.literal("§e/commandcontrols luckperms-setup §7- Setup LuckPerms permissions"));
        source.sendMessage(Text.literal("§e/commandcontrols status §7- Show current status"));
        return 1;
    }
    
    private static int executeResync(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        var server = source.getServer();
        
        // Resync all players
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            CommandControl.sendSyncToPlayer(player);
            server.getPlayerManager().sendCommandTree(player);
        }
        
        int playerCount = server.getPlayerManager().getPlayerList().size();
        source.sendMessage(Text.literal("§a[CommandControls] Resynced commands for " + playerCount + " player(s)"));
        return 1;
    }
    
    private static int executeReload(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        
        try {
            CommandControlConfig.reload();
            source.sendMessage(Text.literal("§a[CommandControls] Configuration reloaded successfully!"));
            CommandControl.LOGGER.info("[CommandControls] Config reloaded by {}", 
                    source.getName());
        } catch (Exception e) {
            source.sendMessage(Text.literal("§c[CommandControls] Failed to reload config: " + e.getMessage()));
            CommandControl.LOGGER.error("[CommandControls] Failed to reload config", e);
        }
        
        return 1;
    }
    
    private static int executeStatus(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        
        source.sendMessage(Text.literal("§6=== CommandControls Status ==="));
        
        // Show rank hierarchy
        List<String> ranks = CommandControlConfig.getRankHierarchy();
        source.sendMessage(Text.literal("§eRank Hierarchy: §f" + String.join(" < ", ranks)));
        
        // If sender is a player, show their rank
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            var allowedCommands = CommandControlConfig.getAllowedCommandsForPlayer(player);
            source.sendMessage(Text.literal("§eYour allowed commands: §f" + allowedCommands.size()));
        }
        
        return 1;
    }
    
    private static int executeLuckPermsSetup(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        var server = source.getServer();
        
        source.sendMessage(Text.literal("§6[CommandControls] Setting up LuckPerms permissions..."));
        
        List<String> ranks = CommandControlConfig.getRankHierarchy();
        int successCount = 0;
        
        for (String rank : ranks) {
            String command = "lp group " + rank + " permission set group." + rank + " true";
            
            try {
                // Execute the LuckPerms command
                server.getCommandManager().executeWithPrefix(
                    server.getCommandSource().withSilent(),
                    command
                );
                
                source.sendMessage(Text.literal("§a  ✓ Set group." + rank + " for group " + rank));
                successCount++;
            } catch (Exception e) {
                source.sendMessage(Text.literal("§c  ✗ Failed to set group." + rank + ": " + e.getMessage()));
                CommandControl.LOGGER.warn("[CommandControls] Failed to execute: {}", command, e);
            }
        }
        
        if (successCount == ranks.size()) {
            source.sendMessage(Text.literal("§a[CommandControls] LuckPerms setup complete! All " + successCount + " groups configured."));
        } else {
            source.sendMessage(Text.literal("§e[CommandControls] Setup partially complete. " + successCount + "/" + ranks.size() + " groups configured."));
            source.sendMessage(Text.literal("§7Make sure LuckPerms is installed and the groups exist."));
        }
        
        source.sendMessage(Text.literal("§7Tip: Create missing groups with: /lp creategroup <name>"));
        
        return 1;
    }
}
