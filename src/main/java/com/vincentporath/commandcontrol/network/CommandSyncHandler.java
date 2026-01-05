package com.vincentporath.commandcontrol.network;

import com.vincentporath.commandcontrol.CommandControl;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Network handler for syncing allowed commands from server to client
 */
public class CommandSyncHandler {
    
    public static final Identifier SYNC_CHANNEL = new Identifier(CommandControl.MOD_ID, "command_sync");
    
    // Special marker for full access (OP players)
    private static final int FULL_ACCESS_MARKER = -1;
    
    /**
     * Create a packet buffer with the allowed commands
     */
    public static PacketByteBuf createSyncPacket(Set<String> allowedCommands) {
        PacketByteBuf buf = PacketByteBufs.create();
        
        // Write the number of commands
        buf.writeVarInt(allowedCommands.size());
        
        // Write each command
        for (String command : allowedCommands) {
            buf.writeString(command);
        }
        
        return buf;
    }
    
    /**
     * Create a packet indicating full access (OP player)
     */
    public static PacketByteBuf createFullAccessPacket() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(FULL_ACCESS_MARKER); // -1 means full access
        return buf;
    }
    
    /**
     * Read allowed commands from a packet buffer
     * Returns null if this is a full access packet
     */
    public static Set<String> readSyncPacket(PacketByteBuf buf) {
        int count = buf.readVarInt();
        
        // Check for full access marker
        if (count == FULL_ACCESS_MARKER) {
            return null; // null = full access
        }
        
        Set<String> commands = new HashSet<>();
        for (int i = 0; i < count; i++) {
            commands.add(buf.readString());
        }
        
        return commands;
    }
}
