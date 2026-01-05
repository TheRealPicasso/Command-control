# Command Controls

**Take full control over which commands your players can see and use!**

Command Controls is a powerful server management mod that lets you configure command access based on player ranks - with seamless **LuckPerms integration**.

---

## ✨ Features

### 🔒 Complete Command Control
- **Hide commands** from tab-complete - players only see what they're allowed to use
- **Block execution** of unauthorized commands
- **Rank-based permissions** - configure different commands for different player groups

### 🔗 LuckPerms Integration
- Automatically detects player groups from LuckPerms
- Commands resync instantly when a player's rank changes
- One-click setup command: `/commandcontrols luckperms-setup`

### 🎮 Mod Compatibility
- Works with **vanilla commands** AND **mod commands**
- Compatible with mods using standard Minecraft permission checks (`hasPermissionLevel`)
- Examples: Create, WorldEdit, Carpet Mod, Essential Commands, etc.
- Client-side filtering for mods like Xaero's Minimap, Litematica, etc.

> ⚠️ **Note:** Mods with their own permission systems (like Impactor) need permissions set directly in LuckPerms, not through this mod.

---

## 📦 Installation

### Server (Required)
1. Install [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Drop the mod JAR into your `mods` folder
3. (Recommended) Install [LuckPerms](https://luckperms.net/) for rank-based permissions

### Client (Optional)
Install on clients to also hide client-side mod commands.

---

## ⚙️ Configuration

Edit `config/commandcontrols/commands.json`:

```json
{
  "rank_hierarchy": ["default", "premium", "vip", "vip+", "moderator", "admin", "owner"],
  "bypass_commands": ["help"],
  "commands": {
    "all_ranks": ["help", "msg", "me", "seed", "list"],
    "vip": ["nick", "hat", "fly"],
    "moderator": ["kick", "tp", "teleport", "clear", "spectate"],
    "admin": ["ban", "give", "gamemode", "time", "weather", "effect", "summon"],
    "owner": ["stop", "op", "deop", "whitelist", "luckperms", "reload"]
  }
}
```

**How it works:**
- `rank_hierarchy` - Ranks from lowest to highest (higher ranks inherit lower rank commands)
- `all_ranks` - Commands everyone can use
- `[rank]` - Commands for that specific rank and above

---

## 🎯 LuckPerms Setup

### Automatic (Recommended)
Just run as OP:
```
/commandcontrols luckperms-setup
```
This automatically configures LuckPerms for all your ranks!

### Fallback (No LuckPerms)
Without LuckPerms, the mod uses vanilla OP levels:
- OP 4 → owner
- OP 3 → admin  
- OP 2 → moderator
- Everyone else → default

---

## 📋 Commands

| Command | Description |
|---------|-------------|
| `/commandcontrols reload` | Reload configuration |
| `/commandcontrols luckperms-setup` | Auto-setup LuckPerms |
| `/commandcontrols status` | Show your current rank and allowed commands |
| `/commandcontrols resync` | Synchronizes your rank permissions |

*All commands require OP level 4*

---

## 🔧 Technical Details

- Overrides command permissions via reflection (similar to Player Roles)
- Intercepts command tree packets for visibility filtering
- Tracks command execution context for permission checks
- Listens to LuckPerms events for instant resync on rank changes

---

## 📝 Requirements

- Minecraft **1.20.1**
- [Fabric Loader](https://fabricmc.net/) 0.14.21+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [fabric-permissions-api](https://modrinth.com/mod/fabric-permissions-api) (included/bundled)

### Optional
- [LuckPerms](https://luckperms.net/) - For rank-based permissions

---

## ❓ FAQ

**Q: Does this work with mod commands?**  
A: Yes, for mods that use Minecraft's standard `hasPermissionLevel()` check (most mods). Mods with custom permission systems (like Impactor, which uses Cloud Commands) need their permissions set directly in LuckPerms.

**Q: Do players need the mod installed?**  
A: No, server-only works fine. But installing on clients hides client-side mod commands too.

**Q: Commands are visible but won't execute?**  
A: Make sure the command is in your config file and the player has the correct LuckPerms group.