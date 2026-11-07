# Minecraft MCP

**Let AI agents drive your Minecraft client, built for server developers.**

Minecraft MCP embeds a [Model Context Protocol](https://modelcontextprotocol.io) server directly in your Minecraft client. Install it, start the game, and any MCP-compatible AI agent (Claude Code, Claude Desktop, Cursor, and others) can play, build, test and debug on your behalf.

## Why?

Developing a Minecraft server means a lot of repetitive in-game work: running commands, building test setups, checking behavior, reading logs. With this mod, your AI assistant does it for you, inside a real running game, and sees the same thing you see.

## Features

| Tool | What it does |
|---|---|
| `server_status` | World loaded, MSPT, tick rate, dimensions, players |
| `run_command` | Runs any command and returns its result and output |
| `get_player` | Position, health, food, inventory, effects |
| `list_entities` | Entities around a player or a point, nearest first |
| `inspect_entity` | Attributes, effects, equipment and motion of an entity |
| `inspect_item` | Full details of one inventory stack: name, lore, enchantments, durability, tooltip, all data components |
| `read_logs` | Game log with a cursor and filters (level, logger, text) |
| `read_combat_log` | Damage and death events: who hit whom, and how much |
| `take_screenshot` | PNG of what the player sees |
| `client_state` | Open screen, crosshair target, received chat |
| `client_input` | Look, hotbar, use, attack, hold keys, respawn, chat |
| `client_container` | Open the inventory, list slots, hover a slot (the tooltip shows in screenshots), click slots; works with plugin menus |
| `list_commands` | Commands the server allows you, with syntax; detects WorldEdit |
| `client_world` | List, create, open and leave singleplayer worlds; join a multiplayer server |

Also includes an in-game `/mcp status` command, and a config screen if Mod Menu and Cloth Config are installed (both optional).

**WorldEdit (optional):** if WorldEdit is installed on the server, its `//` commands work through `run_command` (`//pos1 0,64,0`, `//set stone`, `//undo`), and `list_commands` shows their syntax.

## Remote servers

Join any server (ideally as an operator) with `client_world` (`action=join`). `run_command` is then sent through the chat as your player with the server's answer returned, `list_commands` shows what you may run (WorldEdit included), and `server_status`, `get_player` and `inspect_item` work from your client's view. The command deny list still applies.

## Quick start

1. Install [Fabric Loader](https://fabricmc.net) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Drop the mod in your `mods` folder and start Minecraft 26.3.
3. Connect your AI client to the endpoint:

```bash
claude mcp add --transport http minecraft http://127.0.0.1:25580/mcp
```

4. Ask your agent to start with `server_status`.

> The game keeps running while the AI thinks. Park the player somewhere safe, or use `/tick freeze`, between action sequences.

## Configuration

A config file is created on first launch in `config/minecraft_mcp.json`: port, optional auth token, command deny list, log buffer size, screenshot size, and more.

## Security

The endpoint gives full operator control over your game, so it is locked down by default:

- Binds to **loopback only** (`127.0.0.1`) and rejects non-local browser origins.
- Refuses to start on a non-loopback address without an auth token.
- `run_command` uses a **deny list** (`stop`, `op`, `ban`, …), also applied behind `/execute … run`. It is a guard rail against mistakes, not a sandbox.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.20.1+ and Fabric API
- Java 25

## Links

- Source code and issues: https://github.com/GrayR0ot/minecraft-mcp
- License: Unlicense (public domain)
