package com.vincentporath.commandcontrol;

import com.vincentporath.commandcontrol.command.CommandControlCommand;
import com.vincentporath.commandcontrol.config.CommandControlConfig;
import com.vincentporath.commandcontrol.network.CommandSyncHandler;
import com.vincentporath.commandcontrol.override.CommandRequirementOverride;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Command Control - A Fabric mod for controlling command visibility and access
 * 
 * Features:
 * - Block commands per rank (integrates with LuckPerms)
 * - Hide command suggestions from tab-complete
 * - Sync allowed commands to client for client-side filtering
 * - Configurable via JSON config file
 */
public class CommandControl implements ModInitializer {
    
    public static final String MOD_ID = "commandcontrols";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Track OP status to detect changes (checked less frequently as backup)
    private static final Map<UUID, Boolean> playerOpStatus = new HashMap<>();
    private static int tickCounter = 0;
    
    // Track if we need to do a full resync (after /op or /deop command)
    private static boolean pendingOpResync = false;
    private static MinecraftServer serverInstance = null;
    
    @Override
    public void onInitialize() {
        LOGGER.info("==========================================");
        LOGGER.info("Command Controls v1.0.0 is initializing...");
        LOGGER.info("Control command visibility and access");
        LOGGER.info("==========================================");
        
        // Load configuration
        CommandControlConfig.initialize();
        
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CommandControlCommand.register(dispatcher);
            LOGGER.info("[CommandControls] Commands registered");
        });
        
        // Register events
        registerEvents();
        
        LOGGER.info("Command Control initialization complete!");
    }
    
    /**
     * Called when /op or /deop is executed - triggers resync for all players
     */
    public static void triggerOpResync() {
        pendingOpResync = true;
    }
    
    /**
     * Send command sync to a specific player
     */
    public static void sendSyncToPlayer(ServerPlayerEntity player) {
        try {
            // OP level 4 sees all commands - send empty sync to tell client to show everything
            if (player.hasPermissionLevel(4)) {
                // Send special "full access" packet - empty set means show all
                if (ServerPlayNetworking.canSend(player, CommandSyncHandler.SYNC_CHANNEL)) {
                    ServerPlayNetworking.send(player, CommandSyncHandler.SYNC_CHANNEL, 
                            CommandSyncHandler.createFullAccessPacket());
                    LOGGER.info("[CommandControls] Sent FULL ACCESS sync to {}", player.getName().getString());
                }
                return;
            }
            
            var allowedCommands = CommandControlConfig.getAllowedCommandsForPlayer(player);
            var hiddenCommands = CommandControlConfig.getHiddenCommands();
            
            LOGGER.info("[CommandControls] Sending sync packet to {} with {} commands (hidden: {}): {}", 
                    player.getName().getString(), allowedCommands.size(), hiddenCommands.size(), allowedCommands);
            
            // Check if client can receive our packets
            if (ServerPlayNetworking.canSend(player, CommandSyncHandler.SYNC_CHANNEL)) {
                // Send the allowed commands and hidden commands to the client
                ServerPlayNetworking.send(player, CommandSyncHandler.SYNC_CHANNEL, 
                        CommandSyncHandler.createSyncPacket(allowedCommands, hiddenCommands));
            } else {
                LOGGER.warn("[CommandControls] Client cannot receive sync packets for {}", player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.warn("[CommandControls] Failed to send command sync to player", e);
        }
    }
    
    private void registerEvents() {
        // Initialize LuckPerms integration when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            LuckPermsIntegration.initialize(server);
            
            // Apply command permission overrides AFTER all commands are registered
            // This is the key - we override the requirement predicates using reflection
            CommandRequirementOverride.applyOverrides(server.getCommandManager().getDispatcher());
        });
        
        // Re-apply overrides after datapack reload (commands may be re-registered)
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                LOGGER.info("[CommandControls] Datapack reload detected, re-applying command overrides...");
                CommandRequirementOverride.applyOverrides(server.getCommandManager().getDispatcher());
                
                // Resync all players' command trees
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    server.getPlayerManager().sendCommandTree(player);
                }
            }
        });
        
        // When a player joins, send them the allowed commands list
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            serverInstance = server;
            // Small delay to ensure client is ready
            server.execute(() -> {
                var player = handler.getPlayer();
                // Track initial OP status
                playerOpStatus.put(player.getUuid(), player.hasPermissionLevel(4));
                sendSyncToPlayer(player);
            });
        });
        
        // Clean up when player disconnects
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            playerOpStatus.remove(handler.getPlayer().getUuid());
        });
        
        // Check for pending OP resync (triggered by mixin) and backup polling every 5 seconds
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            serverInstance = server;
            
            // Check for pending resync from /op or /deop command (immediate)
            if (pendingOpResync) {
                pendingOpResync = false;
                resyncAllPlayers(server);
            }
            
            // Backup: Check for OP status changes every 5 seconds (100 ticks)
            // This catches edge cases like external tools modifying ops.json
            tickCounter++;
            if (tickCounter >= 100) {
                tickCounter = 0;
                checkOpStatusChanges(server);
            }
        });
        
        LOGGER.info("[CommandControls] Events registered");
    }
    
    private static void resyncAllPlayers(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            boolean currentlyOp = player.hasPermissionLevel(4);
            playerOpStatus.put(uuid, currentlyOp);
            
            // Resend sync
            sendSyncToPlayer(player);
            
            // Also update the command tree
            server.getPlayerManager().sendCommandTree(player);
        }
    }
    
    private void checkOpStatusChanges(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            boolean currentlyOp = player.hasPermissionLevel(4);
            Boolean previouslyOp = playerOpStatus.get(uuid);
            
            if (previouslyOp != null && previouslyOp != currentlyOp) {
                playerOpStatus.put(uuid, currentlyOp);
                
                // Resend sync with updated permissions
                sendSyncToPlayer(player);
                
                // Also update the command tree
                server.getPlayerManager().sendCommandTree(player);
            }
        }
    }
}
