# CommandControls Wiki

A powerful Fabric mod for Minecraft 1.20.1 that provides rank-based command visibility and access control. Integrates with LuckPerms for permission management.

---

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Configuration](#configuration)
  - [Rank Hierarchy](#rank-hierarchy)
  - [Bypass Commands](#bypass-commands)
  - [Hidden Commands](#hidden-commands)
  - [Aliases](#aliases)
  - [Commands per Rank](#commands-per-rank)
- [How It Works](#how-it-works)
- [LuckPerms Integration](#luckperms-integration)
- [Mod Compatibility](#mod-compatibility)
- [Commands](#commands)
- [Troubleshooting](#troubleshooting)

---

## Features

- **Rank-Based Command Filtering**: Only show commands that players are allowed to use based on their rank
- **LuckPerms Integration**: Automatically detects player ranks via LuckPerms permission groups
- **Client-Side Sync**: Syncs allowed commands to the client for proper tab-completion filtering
- **Hidden Commands**: Allow commands to work but hide them from tab-complete suggestions
- **Command Aliases**: Define aliases that map to target commands - if the target is allowed, the alias works too
- **Hierarchical Permissions**: Higher ranks inherit commands from lower ranks
- **Hot-Reload Support**: Reload configuration without server restart using `/commandcontrols reload`
- **Bypass Commands**: Define commands that are always visible to everyone

---

## Installation

### Requirements
- Minecraft 1.20.1
- Fabric Loader 0.16.10+
- Fabric API 0.92.6+
- LuckPerms (recommended for rank integration)
- fabric-permissions-api (included as dependency)

### Setup
1. Download the mod JAR file
2. Place it in your `mods` folder (both server and client)
3. Start the server to generate the default configuration
4. Edit `config/commandcontrols/commands.json` to configure ranks and commands
5. Use `/commandcontrols reload` to apply changes

---

## Configuration

The configuration file is located at `config/commandcontrols/commands.json`.

### Example Configuration

```json
{
  "_comment": "Command Controls Configuration - Define which commands each rank can see and use",
  "_hidden_info": "Hidden commands work but don't show in tab-complete (useful for alias targets)",
  "rank_hierarchy": [
    "default",
    "premium",
    "vip",
    "vip+",
    "mod",
    "admin",
    "owner"
  ],
  "bypass_commands": [
    "help"
  ],
  "hidden_commands": [
    "sidebar"
  ],
  "aliases": {
    "sb": "sidebar",
    "sideboard": "sidebar",
    "infoboard": "sidebar",
    "b": "balance"
  },
  "commands": {
    "all_ranks": [
      "help",
      "me",
      "msg",
      "balance",
      "pay",
      "baltop",
      "sb",
      "sideboard",
      "infoboard"
    ],
    "vip": [],
    "mod": [
      "kick",
      "tp",
      "teleport",
      "spectate"
    ],
    "admin": [
      "ban",
      "gamemode",
      "give"
    ],
    "owner": [
      "op",
      "stop",
      "luckperms"
    ]
  }
}
```

### Rank Hierarchy

Define the order of ranks from lowest to highest. Players inherit commands from all ranks below their current rank.

```json
"rank_hierarchy": [
  "default",    // Lowest rank
  "premium",
  "vip",
  "vip+",
  "mod",
  "admin",
  "owner"       // Highest rank
]
```

**Important**: Rank names must match your LuckPerms group names (case-insensitive).

### Bypass Commands

Commands that are always visible and usable by everyone, regardless of rank.

```json
"bypass_commands": [
  "help"
]
```

### Hidden Commands

Commands that are **allowed** (they work when typed) but are **hidden** from tab-complete suggestions. This is useful for hiding the "real" command when you want players to use an alias instead.

```json
"hidden_commands": [
  "sidebar"
]
```

**Use Case**: You have `/sidebar` as the real command but want players to use `/sb` instead. Add `sidebar` to `hidden_commands` and `sb` to `all_ranks`.

### Aliases

Map short command names to their target commands. If a player is allowed to use the target command (or any alias of it), all related aliases will work.

```json
"aliases": {
  "sb": "sidebar",
  "sideboard": "sidebar",
  "infoboard": "sidebar",
  "b": "balance"
}
```

**How it works**:
- If `sb` is in `all_ranks`, and `sb` is an alias for `sidebar`, then `/sidebar` will also work
- The alias resolution works bidirectionally

### Commands per Rank

Define which commands each rank can access. Commands are defined per rank, and players inherit commands from all lower ranks.

```json
"commands": {
  "all_ranks": ["help", "me", "msg"],
  "vip": ["home", "sethome"],
  "mod": ["kick", "mute", "tp"],
  "admin": ["ban", "gamemode", "give"],
  "owner": ["op", "stop", "reload"]
}
```

**Special key**: `all_ranks` - Commands available to ALL players regardless of rank.

---

## How It Works

### Server-Side
1. When a player joins, CommandControls checks their LuckPerms rank
2. Based on their rank and the configuration, it builds a filtered command tree
3. Only allowed commands are sent to the player's client
4. OP players (permission level 4) bypass all restrictions and see all commands

### Client-Side
1. The client receives a sync packet with the list of allowed commands and hidden commands
2. The client-side mixin filters tab-complete suggestions based on this list
3. Hidden commands are excluded from suggestions but will still work when typed

### Command Execution
- Commands are filtered at the **visibility** level, not execution level
- If a player somehow knows a command they shouldn't have access to, they still can't execute it (vanilla Minecraft permission system)
- The mod enhances security by also hiding commands from the command tree

---

## LuckPerms Integration

CommandControls uses LuckPerms to determine player ranks. It checks for `group.<rankname>` permissions.

### Setup
1. Install LuckPerms on your server
2. Create groups matching your `rank_hierarchy` names:
   ```
   /lp creategroup default
   /lp creategroup vip
   /lp creategroup mod
   /lp creategroup admin
   /lp creategroup owner
   ```
3. Assign players to groups:
   ```
   /lp user <player> parent set <group>
   ```

### How Rank Detection Works
The mod checks from highest to lowest rank and returns the first match:
```
owner → admin → mod → vip+ → vip → premium → default
```

If a player is in both `vip` and `mod` groups, they will be treated as `mod` (the higher rank).

---

## Mod Compatibility

### Fully Supported
- **Vanilla Minecraft commands** - Full support
- **Fabric API commands** - Full support
- **LuckPerms** - Required for rank detection
- **Most Fabric mods** - Commands using standard Brigadier registration

### Partial Support
- **Mods with custom permission systems** (like Impactor) - You need to set permissions directly in LuckPerms for these mods

### Known Compatible Mods
- StyledSidebars
- CommandAliases
- Impactor (with LuckPerms permissions)
- WorldEdit
- And many more...

---

## Commands

### `/commandcontrols reload`
Reloads the configuration from file without restarting the server.

**Permission**: Requires OP level 4

**Usage**:
```
/commandcontrols reload
```

---

## Troubleshooting

### Commands not showing in tab-complete

1. **Check the config**: Make sure the command is in `all_ranks` or the appropriate rank
2. **Check aliases**: If you're using an alias, make sure both the alias AND the target command logic is correct
3. **Reload config**: Use `/commandcontrols reload` after changes
4. **Rejoin the server**: Command lists are synced on join

### Hidden commands still showing

1. Make sure the command is in `hidden_commands` array
2. Make sure both server AND client have the updated mod
3. Rejoin the server after config changes

### Player has wrong rank

1. Check LuckPerms groups: `/lp user <player> info`
2. Make sure the group name matches exactly (case-insensitive)
3. Make sure the rank is in `rank_hierarchy`

### Debug Logging

The mod outputs debug information to the server log:
```
[CommandControls] Building command tree for <player>. Available commands: [...]
[CommandControls] Sent X commands to <player>: [...]
[CommandControls] Sending sync packet to <player> with X commands (hidden: Y)
```

---

## Technical Details

### Sync Packet Format
The mod sends a custom packet to clients containing:
1. List of allowed commands
2. List of hidden commands

### Mixin Targets
- `CommandManager` - Filters the command tree sent to clients
- `ChatInputSuggestor` (client) - Filters tab-complete suggestions

### Config Auto-Reload
The configuration automatically reloads every 30 seconds if changes are detected.

---

## License

This mod is licensed under the MIT License.

---

## Support

For issues and feature requests, please open an issue on the GitHub repository.
