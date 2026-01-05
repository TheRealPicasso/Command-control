package com.vincentporath.commandcontrol.client;

import com.vincentporath.commandcontrol.network.CommandSyncHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side initialization for Command Control
 * Receives allowed commands from server and filters client-side suggestions
 */
@Environment(EnvType.CLIENT)
public class CommandControlClient implements ClientModInitializer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("commandcontrol-client");
    
    // Set of commands the player is allowed to use (synced from server)
    private static Set<String> allowedCommands = new HashSet<>();
    
    // Set of commands that are hidden from tab-complete but still work
    private static Set<String> hiddenCommands = new HashSet<>();
    
    // Whether we've received sync from a CommandControl-enabled server
    private static boolean syncReceived = false;
    
    // Whether the player has full access (OP)
    private static boolean fullAccess = false;
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("[CommandControls] Client initializing...");
        
        // Register to receive command sync from server
        ClientPlayNetworking.registerGlobalReceiver(CommandSyncHandler.SYNC_CHANNEL, (client, handler, buf, responseSender) -> {
            // Read the sync data on network thread using new V2 format
            CommandSyncHandler.SyncData syncData = CommandSyncHandler.readSyncPacketV2(buf);
            
            // Update state on client thread
            client.execute(() -> {
                if (syncData.fullAccess) {
                    // Full access packet (OP player)
                    fullAccess = true;
                    allowedCommands = new HashSet<>();
                    hiddenCommands = new HashSet<>();
                    syncReceived = true;
                    LOGGER.info("[CommandControls] Received FULL ACCESS from server (OP mode)");
                } else {
                    // Normal allowed commands list
                    fullAccess = false;
                    allowedCommands = syncData.allowedCommands;
                    hiddenCommands = syncData.hiddenCommands != null ? syncData.hiddenCommands : new HashSet<>();
                    syncReceived = true;
                    LOGGER.info("[CommandControls] Received {} allowed commands, {} hidden from server", 
                            allowedCommands.size(), hiddenCommands.size());
                }
            });
        });
        
        // Clear state when disconnecting
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            allowedCommands.clear();
            hiddenCommands.clear();
            syncReceived = false;
            fullAccess = false;
            LOGGER.debug("[CommandControls] Cleared command sync state");
        });
        
        LOGGER.info("[CommandControls] Client initialized!");
    }
    
    /**
     * Check if a command should be shown in suggestions
     * @param commandName The base command name (without /)
     * @return true if the command should be shown in tab-complete
     */
    public static boolean shouldShowCommand(String commandName) {
        // If we haven't received sync from server, show all commands (vanilla behavior)
        if (!syncReceived) {
            return true;
        }
        
        // Full access means show everything
        if (fullAccess) {
            return true;
        }
        
        String lowerCommand = commandName.toLowerCase();
        
        // Hidden commands are allowed but not shown in suggestions
        if (hiddenCommands.contains(lowerCommand)) {
            LOGGER.debug("[CommandControls] Hiding command from suggestions: {}", lowerCommand);
            return false;
        }
        
        boolean allowed = allowedCommands.contains(lowerCommand);
        if (!allowed) {
            LOGGER.debug("[CommandControls] Blocking command: {} (not in {} allowed commands)", lowerCommand, allowedCommands.size());
        }
        return allowed;
    }
    
    /**
     * Check if command sync has been received from server
     */
    public static boolean isSyncReceived() {
        return syncReceived;
    }
    
    /**
     * Check if player has full access (OP)
     */
    public static boolean hasFullAccess() {
        return fullAccess;
    }
    
    /**
     * Get the set of allowed commands
     */
    public static Set<String> getAllowedCommands() {
        return allowedCommands;
    }
}
