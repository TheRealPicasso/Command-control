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
     * Create a packet buffer with the allowed commands and hidden commands
     */
    public static PacketByteBuf createSyncPacket(Set<String> allowedCommands, Set<String> hiddenCommands) {
        PacketByteBuf buf = PacketByteBufs.create();
        
        // Write the number of allowed commands
        buf.writeVarInt(allowedCommands.size());
        
        // Write each allowed command
        for (String command : allowedCommands) {
            buf.writeString(command);
        }
        
        // Write the number of hidden commands
        buf.writeVarInt(hiddenCommands.size());
        
        // Write each hidden command
        for (String command : hiddenCommands) {
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
     * Result of reading a sync packet
     */
    public static class SyncData {
        public final Set<String> allowedCommands;
        public final Set<String> hiddenCommands;
        public final boolean fullAccess;
        
        public SyncData(Set<String> allowedCommands, Set<String> hiddenCommands, boolean fullAccess) {
            this.allowedCommands = allowedCommands;
            this.hiddenCommands = hiddenCommands;
            this.fullAccess = fullAccess;
        }
    }
    
    /**
     * Read allowed and hidden commands from a packet buffer
     * Returns SyncData with fullAccess=true if this is a full access packet
     */
    public static SyncData readSyncPacketV2(PacketByteBuf buf) {
        int count = buf.readVarInt();
        
        // Check for full access marker
        if (count == FULL_ACCESS_MARKER) {
            return new SyncData(null, null, true);
        }
        
        // Read allowed commands
        Set<String> allowedCommands = new HashSet<>();
        for (int i = 0; i < count; i++) {
            allowedCommands.add(buf.readString());
        }
        
        // Read hidden commands
        Set<String> hiddenCommands = new HashSet<>();
        int hiddenCount = buf.readVarInt();
        for (int i = 0; i < hiddenCount; i++) {
            hiddenCommands.add(buf.readString());
        }
        
        return new SyncData(allowedCommands, hiddenCommands, false);
    }
}
