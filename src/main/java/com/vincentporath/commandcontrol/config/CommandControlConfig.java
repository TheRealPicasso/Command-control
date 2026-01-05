package com.vincentporath.commandcontrol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vincentporath.commandcontrol.CommandControl;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Configuration manager for Command Control
 * Handles loading/saving config and checking command permissions
 */
public class CommandControlConfig {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "commandcontrols", "commands.json");
    
    // Commands allowed for all ranks
    private static Set<String> allRanksCommands = new HashSet<>();
    
    // Commands that bypass the filter entirely (always shown)
    private static Set<String> bypassCommands = new HashSet<>();
    
    // Commands that are allowed but hidden from tab-complete (e.g., alias targets)
    private static Set<String> hiddenCommands = new HashSet<>();
    
    // Command aliases (alias -> target command)
    private static Map<String, String> commandAliases = new HashMap<>();
    
    // Commands per rank
    private static Map<String, Set<String>> rankCommands = new HashMap<>();
    
    // Rank hierarchy (lowest to highest)
    private static List<String> rankHierarchy = Arrays.asList(
            "default", "premium", "vip", "vip+", "moderator", "admin", "owner"
    );
    
    private static boolean initialized = false;
    private static long lastLoadTime = 0;
    private static final long RELOAD_INTERVAL = 30000; // 30 seconds
    
    /**
     * Initialize the configuration
     */
    public static void initialize() {
        if (!initialized) {
            loadConfig();
            initialized = true;
            CommandControl.LOGGER.info("[CommandControls] Configuration initialized");
        }
    }
    
    /**
     * Load configuration from file
     */
    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                createDefaultConfig();
            }
            
            String content = Files.readString(CONFIG_PATH);
            JsonObject root = GSON.fromJson(content, JsonObject.class);
            
            // Clear existing data
            allRanksCommands.clear();
            bypassCommands.clear();
            hiddenCommands.clear();
            commandAliases.clear();
            rankCommands.clear();
            
            // Load rank hierarchy
            if (root.has("rank_hierarchy")) {
                rankHierarchy = new ArrayList<>();
                for (JsonElement element : root.getAsJsonArray("rank_hierarchy")) {
                    rankHierarchy.add(element.getAsString().toLowerCase());
                }
            }
            
            // Load bypass commands
            if (root.has("bypass_commands")) {
                for (JsonElement element : root.getAsJsonArray("bypass_commands")) {
                    bypassCommands.add(element.getAsString().toLowerCase());
                }
            }
            
            // Load hidden commands (allowed but not shown in tab-complete)
            if (root.has("hidden_commands")) {
                for (JsonElement element : root.getAsJsonArray("hidden_commands")) {
                    hiddenCommands.add(element.getAsString().toLowerCase());
                }
                CommandControl.LOGGER.info("[CommandControls] Loaded {} hidden commands", hiddenCommands.size());
            }
            
            // Load command aliases
            if (root.has("aliases")) {
                JsonObject aliases = root.getAsJsonObject("aliases");
                for (Map.Entry<String, JsonElement> entry : aliases.entrySet()) {
                    commandAliases.put(entry.getKey().toLowerCase(), entry.getValue().getAsString().toLowerCase());
                }
                CommandControl.LOGGER.info("[CommandControls] Loaded {} command aliases", commandAliases.size());
            }
            
            // Load commands section
            if (root.has("commands")) {
                JsonObject commands = root.getAsJsonObject("commands");
                
                // Load all_ranks commands
                if (commands.has("all_ranks")) {
                    for (JsonElement element : commands.getAsJsonArray("all_ranks")) {
                        allRanksCommands.add(element.getAsString().toLowerCase());
                    }
                }
                
                // Load per-rank commands
                for (String rank : rankHierarchy) {
                    if (commands.has(rank)) {
                        Set<String> cmds = new HashSet<>();
                        for (JsonElement element : commands.getAsJsonArray(rank)) {
                            cmds.add(element.getAsString().toLowerCase());
                        }
                        rankCommands.put(rank, cmds);
                    }
                }
            }
            
            lastLoadTime = System.currentTimeMillis();
            
            int totalCommands = allRanksCommands.size() + 
                    rankCommands.values().stream().mapToInt(Set::size).sum();
            CommandControl.LOGGER.info("[CommandControls] Loaded config: {} base commands, {} ranks", 
                    totalCommands, rankHierarchy.size());
            
        } catch (Exception e) {
            CommandControl.LOGGER.error("[CommandControls] Failed to load config", e);
            createDefaultConfig();
        }
    }
    
    /**
     * Create default configuration file
     */
    private static void createDefaultConfig() {
        JsonObject root = new JsonObject();
        
        // Add description
        root.addProperty("_comment", "Command Controls Configuration - Define which commands each rank can see and use");
        root.addProperty("_mod_support", "Works with mods using Minecraft's hasPermissionLevel(). Mods with custom permission systems (like Impactor) need LuckPerms permissions set directly.");
        root.addProperty("_hidden_info", "Hidden commands work but don't show in tab-complete (useful for alias targets)");
        
        // Rank hierarchy
        JsonArray hierarchy = new JsonArray();
        for (String rank : rankHierarchy) {
            hierarchy.add(rank);
        }
        root.add("rank_hierarchy", hierarchy);
        
        // Bypass commands (always visible to everyone)
        JsonArray bypass = new JsonArray();
        bypass.add("help");
        root.add("bypass_commands", bypass);
        
        // Hidden commands (allowed but not shown in tab-complete)
        JsonArray hidden = new JsonArray();
        // Example: hidden.add("sidebar"); - add commands you want to hide from suggestions
        root.add("hidden_commands", hidden);
        
        // Aliases (shorthand commands that point to real commands)
        JsonObject aliases = new JsonObject();
        // Example: aliases.addProperty("sb", "sidebar");
        root.add("aliases", aliases);
        
        // Commands section
        JsonObject commands = new JsonObject();
        
        // All ranks - basic vanilla commands everyone should have
        JsonArray allRanks = new JsonArray();
        allRanks.add("help");
        allRanks.add("me");
        allRanks.add("msg");
        allRanks.add("list");
        allRanks.add("seed");
        commands.add("all_ranks", allRanks);
        
        // VIP commands (empty by default - add your own)
        JsonArray vip = new JsonArray();
        commands.add("vip", vip);
        
        // Moderator commands
        JsonArray moderator = new JsonArray();
        moderator.add("kick");
        moderator.add("tp");
        moderator.add("teleport");
        moderator.add("spectate");
        moderator.add("clear");
        commands.add("moderator", moderator);
        
        // Admin commands
        JsonArray admin = new JsonArray();
        admin.add("ban");
        admin.add("ban-ip");
        admin.add("pardon");
        admin.add("pardon-ip");
        admin.add("banlist");
        admin.add("give");
        admin.add("gamemode");
        admin.add("time");
        admin.add("weather");
        admin.add("difficulty");
        admin.add("effect");
        admin.add("enchant");
        admin.add("experience");
        admin.add("xp");
        admin.add("fill");
        admin.add("setblock");
        admin.add("summon");
        admin.add("kill");
        commands.add("admin", admin);
        
        // Owner commands (server management)
        JsonArray owner = new JsonArray();
        owner.add("stop");
        owner.add("op");
        owner.add("deop");
        owner.add("whitelist");
        owner.add("save-all");
        owner.add("save-off");
        owner.add("save-on");
        owner.add("reload");
        owner.add("function");
        owner.add("data");
        owner.add("datapack");
        owner.add("debug");
        owner.add("gamerule");
        owner.add("worldborder");
        owner.add("forceload");
        owner.add("setworldspawn");
        // LuckPerms commands (if installed)
        owner.add("lp");
        owner.add("luckperms");
        commands.add("owner", owner);
        
        root.add("commands", commands);
        
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(root));
            CommandControl.LOGGER.info("[CommandControls] Created default config at {}", CONFIG_PATH);
        } catch (IOException e) {
            CommandControl.LOGGER.error("[CommandControls] Failed to create default config", e);
        }
        
        // Load the defaults into memory
        allRanksCommands.addAll(Arrays.asList("help", "me", "msg", "list", "seed"));
        bypassCommands.add("help");
    }
    
    /**
     * Check if a command is allowed for a player
     */
    public static boolean isCommandAllowed(ServerPlayerEntity player, String command) {
        // Auto-reload config periodically
        if (System.currentTimeMillis() - lastLoadTime > RELOAD_INTERVAL) {
            loadConfig();
        }
        
        command = command.toLowerCase();
        
        // Debug log
        CommandControl.LOGGER.debug("[CommandControls] isCommandAllowed check: command='{}', allRanksCommands={}", 
            command, allRanksCommands);
        
        // Resolve alias to target command (if command is an alias)
        String resolvedCommand = resolveAlias(command);
        
        // Also check if any alias points to this command (reverse lookup)
        // This allows the target command if any of its aliases are in the allowed list
        Set<String> aliasesForCommand = getAliasesFor(command);
        
        // Bypass commands are always allowed
        if (bypassCommands.contains(command) || bypassCommands.contains(resolvedCommand)) {
            return true;
        }
        for (String alias : aliasesForCommand) {
            if (bypassCommands.contains(alias)) return true;
        }
        
        // All ranks commands - check original, resolved, and any aliases pointing to this command
        if (allRanksCommands.contains(command) || allRanksCommands.contains(resolvedCommand)) {
            return true;
        }
        for (String alias : aliasesForCommand) {
            if (allRanksCommands.contains(alias)) return true;
        }
        
        // Get player's rank
        String playerRank = getPlayerRank(player);
        int playerRankIndex = rankHierarchy.indexOf(playerRank);
        
        // Check all ranks at or below player's rank
        for (int i = 0; i <= playerRankIndex; i++) {
            String rank = rankHierarchy.get(i);
            Set<String> cmds = rankCommands.get(rank);
            if (cmds != null) {
                if (cmds.contains(command) || cmds.contains(resolvedCommand)) {
                    return true;
                }
                for (String alias : aliasesForCommand) {
                    if (cmds.contains(alias)) return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Resolve a command alias to its target command
     * Returns the original command if no alias exists
     */
    public static String resolveAlias(String command) {
        String resolved = commandAliases.get(command.toLowerCase());
        return resolved != null ? resolved : command;
    }
    
    /**
     * Get all aliases that map to a target command
     */
    public static Set<String> getAliasesFor(String targetCommand) {
        Set<String> aliases = new HashSet<>();
        targetCommand = targetCommand.toLowerCase();
        for (Map.Entry<String, String> entry : commandAliases.entrySet()) {
            if (entry.getValue().equals(targetCommand)) {
                aliases.add(entry.getKey());
            }
        }
        return aliases;
    }
    
    /**
     * Get all allowed commands for a player (for syncing to client)
     * This includes hidden commands so they work, but the client will filter them from suggestions
     */
    public static Set<String> getAllowedCommandsForPlayer(ServerPlayerEntity player) {
        // Auto-reload config periodically
        if (System.currentTimeMillis() - lastLoadTime > RELOAD_INTERVAL) {
            loadConfig();
        }
        
        Set<String> allowed = new HashSet<>();
        
        // Add bypass commands
        allowed.addAll(bypassCommands);
        
        // Add all_ranks commands
        allowed.addAll(allRanksCommands);
        
        // Add hidden commands (they need to be allowed for redirects to work)
        allowed.addAll(hiddenCommands);
        
        // Get player's rank
        String playerRank = getPlayerRank(player);
        int playerRankIndex = rankHierarchy.indexOf(playerRank);
        
        // Add commands from all ranks at or below player's rank
        for (int i = 0; i <= playerRankIndex; i++) {
            String rank = rankHierarchy.get(i);
            Set<String> cmds = rankCommands.get(rank);
            if (cmds != null) {
                allowed.addAll(cmds);
            }
        }
        
        // Add all aliases that point to allowed commands
        Set<String> aliasesToAdd = new HashSet<>();
        for (String cmd : allowed) {
            aliasesToAdd.addAll(getAliasesFor(cmd));
        }
        allowed.addAll(aliasesToAdd);
        
        return allowed;
    }
    
    /**
     * Get the set of hidden commands (allowed but not shown in tab-complete)
     */
    public static Set<String> getHiddenCommands() {
        return new HashSet<>(hiddenCommands);
    }
    
    /**
     * Get player's rank from LuckPerms using the simple permission check method
     * As recommended by LuckPerms documentation: https://luckperms.net/wiki/Developer-API-Usage
     */
    private static String getPlayerRank(ServerPlayerEntity player) {
        // LuckPerms recommended method: check group.X permissions
        // This works because LuckPerms automatically grants "group.<groupname>" to players
        // Check from highest to lowest rank to get the highest rank the player has
        for (int i = rankHierarchy.size() - 1; i >= 0; i--) {
            String rank = rankHierarchy.get(i);
            try {
                if (Permissions.check(player, "group." + rank, false)) {
                    return rank;
                }
            } catch (Exception e) {
                // Permission check failed, try next
            }
        }
        
        // Fallback based on OP level
        if (player.hasPermissionLevel(4)) return "owner";
        if (player.hasPermissionLevel(3)) return "admin";
        if (player.hasPermissionLevel(2)) return "moderator";
        
        return "default";
    }
    
    /**
     * Force reload the configuration
     */
    public static void reload() {
        loadConfig();
    }
    
    /**
     * Get the rank hierarchy list
     */
    public static List<String> getRankHierarchy() {
        return new ArrayList<>(rankHierarchy);
    }
}
