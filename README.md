# Command Controls

A Fabric mod for Minecraft 1.20.1+ that gives server owners complete control over which commands players can see and use.

## Features

- **Hide Commands from Tab-Complete**: Unauthorized commands don't appear in suggestions
- **Block Command Execution**: Prevent players from running commands they shouldn't have access to
- **Client-Side Filtering**: Also hides client-side mod commands (like Xaero's Minimap, Litematica, etc.)
- **Rank-Based Permissions**: Configure different commands for different player ranks
- **LuckPerms Integration**: Automatically detects player ranks from LuckPerms
- **Easy Configuration**: Simple JSON config file

## Installation

### Server
1. Install Fabric Loader and Fabric API
2. Place `commandcontrols-x.x.x.jar` in your `mods` folder
3. (Optional) Install LuckPerms for rank-based permissions

### Client
1. Install Fabric Loader and Fabric API
2. Place `commandcontrols-x.x.x.jar` in your `mods` folder

**Note**: The mod works on server-only, but for client-side command filtering (hiding commands from mods like Xaero's Minimap), players need to have it installed too.

## Configuration

Configuration is located at `config/commandcontrols/commands.json`

```json
{
  "_comment": "Command Controls Configuration",
  "rank_hierarchy": ["default", "premium", "vip", "vip+", "moderator", "admin", "owner"],
  "bypass_commands": ["help", "list"],
  "commands": {
    "all_ranks": ["help", "list", "msg", "me", "seed"],
    "vip": ["nick", "hat", "fly"],
    "moderator": ["kick", "mute", "vanish", "tp", "clear"],
    "admin": ["ban", "give", "gamemode", "time", "weather", "effect"],
    "owner": ["stop", "reload", "op", "deop", "luckperms"]
  }
}
```

### Configuration Options

- **rank_hierarchy**: Order of ranks from lowest to highest. Higher ranks inherit commands from lower ranks.
- **bypass_commands**: Commands that are always visible and usable by everyone
- **commands.all_ranks**: Commands available to all players
- **commands.[rank]**: Commands available to players with that rank (and higher)

## How It Works

1. **Server-Side**: The mod intercepts command tree packets and filters out unauthorized commands before sending to clients
2. **Client-Side**: When connected to a CommandControls-enabled server, the client receives a list of allowed commands and filters suggestions locally (including from client-side mods)
3. **Execution Blocking**: Even if a player somehow sends a command they shouldn't have access to, the server blocks execution

## LuckPerms Integration

The mod detects player ranks using LuckPerms `group.<groupname>` permissions.

### Automatic Setup (Recommended)

Run this command as OP to automatically configure LuckPerms for all ranks in your config:

```
/commandcontrols luckperms-setup
```

This will execute `lp group <rank> permission set group.<rank> true` for each rank in your hierarchy.

### Manual Setup

If you prefer to set up manually, run these commands for each group:

```
/lp group default permission set group.default true
/lp group premium permission set group.premium true
/lp group vip permission set group.vip true
/lp group vip+ permission set group.vip+ true
/lp group moderator permission set group.moderator true
/lp group admin permission set group.admin true
/lp group owner permission set group.owner true
```

**Note:** Make sure the groups exist first! Create them with `/lp creategroup <name>` if needed.

### Fallback (No LuckPerms)

If LuckPerms is not installed, the mod falls back to Minecraft's OP levels:
- OP Level 4 = owner (full access)
- OP Level 3 = admin
- OP Level 2 = moderator
- Everyone else = default

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/commandcontrols` | OP Level 4 | Show help |
| `/commandcontrols reload` | OP Level 4 | Reload configuration |
| `/commandcontrols luckperms-setup` | OP Level 4 | Auto-setup LuckPerms permissions |
| `/commandcontrols status` | OP Level 4 | Show current status and your allowed commands |

## Compatibility

- Minecraft 1.20.1
- Fabric Loader 0.14.21+
- Fabric API
- Works with LuckPerms (optional)
- Compatible with mods using Minecraft's standard permission system (`hasPermissionLevel`)

### Mod Compatibility

**Works with:**
- All vanilla commands
- Create, WorldEdit, Carpet Mod, Essential Commands
- Most mods that use `.requires(source -> source.hasPermissionLevel(X))`

**Requires separate LuckPerms permissions:**
- Mods with custom permission systems (e.g., Impactor uses Cloud Commands with `@Permission` annotations)
- These mods check permissions directly via LuckPerms API, bypassing Minecraft's system

## License

MIT License - See [LICENSE](LICENSE) for details.

## Links

- [Modrinth](https://modrinth.com/mod/command-controls)
- [GitHub](https://github.com/TheRealPicasso/Command-controls)
- [Issues](https://github.com/TheRealPicasso/Command-controls/issues)
