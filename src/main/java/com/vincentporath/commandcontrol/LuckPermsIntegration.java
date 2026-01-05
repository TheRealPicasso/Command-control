package com.vincentporath.commandcontrol;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * LuckPerms integration for automatic command resync when permissions change
 */
public class LuckPermsIntegration {
    
    private static boolean initialized = false;
    private static MinecraftServer server = null;
    
    /**
     * Try to hook into LuckPerms events
     * Called after server starts
     */
    public static void initialize(MinecraftServer minecraftServer) {
        if (initialized) return;
        
        server = minecraftServer;
        
        try {
            LuckPerms api = LuckPermsProvider.get();
            EventBus eventBus = api.getEventBus();
            
            // Get the mod container for LuckPerms event registration
            Object plugin = FabricLoader.getInstance()
                .getModContainer(CommandControl.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Could not find commandcontrols mod container"));
            
            // Listen for when a user's data is recalculated (permissions changed)
            eventBus.subscribe(plugin, UserDataRecalculateEvent.class, event -> {
                UUID uuid = event.getUser().getUniqueId();
                
                // Run on main server thread
                if (server != null) {
                    server.execute(() -> {
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                        if (player != null) {
                            // Resync this player's commands
                            CommandControl.sendSyncToPlayer(player);
                            server.getPlayerManager().sendCommandTree(player);
                        }
                    });
                }
            });
            
            initialized = true;
            CommandControl.LOGGER.info("[CommandControls] LuckPerms integration enabled - commands will auto-sync on permission changes");
            
        } catch (IllegalStateException e) {
            // LuckPerms not loaded yet or not installed
            CommandControl.LOGGER.info("[CommandControls] LuckPerms not found - manual resync required after rank changes");
        } catch (Exception e) {
            CommandControl.LOGGER.warn("[CommandControls] Failed to initialize LuckPerms integration", e);
        }
    }
    
    /**
     * Update server reference (called on server start)
     */
    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }
}
