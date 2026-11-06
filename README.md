# Minecraft MCP

A Fabric mod for **Minecraft 26.3** that embeds a [Model Context Protocol](https://modelcontextprotocol.io)
server in the game client, to assist **Minecraft server developers**. Install it on your client, and an
AI agent (Claude Code, or any MCP client) can drive the game for you: run commands, build, test your
server features, inspect players and entities, take screenshots, read logs, create and load worlds.
If WorldEdit is installed on the server, its commands are usable through `run_command` too (optional).

> Status: early foundation (0.0.1).

## Features

| Tool | Side | What it does |
|---|---|---|
| `server_status` | common | World loaded?, MSPT, tick rate, dimensions, players |
| `run_command` | common | Runs a command (as console or a player), returns success, result and output |
| `get_player` | common | Position, health, food, inventory, effects… |
| `list_entities` | common | Entities around a player or a point, nearest first |
| `inspect_entity` | common | Attributes, effects, equipment, motion of one entity |
| `read_logs` | common | Game log with a cursor and filters (level, logger, text) |
| `read_combat_log` | common | Damage and death events: who hit whom, incoming damage vs health actually lost |
| `take_screenshot` | client | PNG of what the player sees |
| `client_state` | client | Open screen, crosshair target, received chat |
| `client_input` | client | Look, hotbar slot, use, attack, hold a key for N ticks (bow, eating, walking), respawn, close screen, chat |
| `client_world` | client | List, create, open and leave singleplayer worlds |

Also: an in-game `/mcp status` command.

## Requirements

- JDK 25+ (the build compiles with `--release 25`)
- Minecraft 26.3, Fabric Loader 0.20.1+, Fabric API

## Quick start (development)

```bash
./gradlew runClient
```

The game starts with player name `McpTester`, and the MCP endpoint listens on
`http://127.0.0.1:25580/mcp`. This repository ships a `.mcp.json`, so Claude Code opened in this
folder sees the `minecraft` server automatically (approve it on first use, or run `/mcp` to
reconnect after starting the game). For another client:

```bash
claude mcp add --transport http minecraft http://127.0.0.1:25580/mcp
```

A typical AI session goes like this: `server_status` → `client_world` (`create`, preset `flat`) →
`run_command` / `client_input` → `take_screenshot` / `read_logs` / `read_combat_log` to check the
result.

> **The game keeps running while the AI thinks.** Park the player somewhere safe (or use
> `/tick freeze`) between action sequences, or it will be attacked while idle.

Known limitation: continuous mining (`hold` + `attack`) requires the game window to have focus,
because vanilla only mines while the mouse is grabbed.

## Configuration

`config/minecraft_mcp.json` is created on first launch:

| Key | Default | Meaning |
|---|---|---|
| `server.enabled` | `true` | Turns the MCP endpoint on or off |
| `server.host` / `server.port` | `127.0.0.1` / `25580` | Bind address (env override: `MCP_PORT`) |
| `server.authToken` | `""` | Optional bearer token (env override: `MCP_TOKEN`) |
| `server.gameThreadTimeoutMs` | `10000` | Maximum wait for the game thread |
| `commands.mode` | `RESTRICTED` | `UNRESTRICTED` disables the deny list |
| `commands.denied` | `stop, op, ban…` | Commands refused in restricted mode, even behind `/execute … run` |
| `logs.bufferSize` | `2000` | Log lines kept in memory |
| `client.disablePauseOnLostFocus` | `true` | Keeps the game running when the window is in the background |
| `client.screenshotMaxWidth` | `960` | Default screenshot width |

### Security

The endpoint gives full operator control over the game. It binds to loopback, rejects non-local
browser `Origin`s (DNS-rebinding protection), and refuses to start on a non-loopback address without
a token. The command deny list is a guard rail against mistakes, **not** a sandbox.

## Architecture

```
com.minecraftmcp
├── core     composition root (McpMod), runtime, config, /mcp command
├── mcp      protocol layer, with no game logic
│   ├── protocol   JSON-RPC 2.0 + MCP lifecycle/tools handler (pure Java, unit tested)
│   ├── transport  Streamable HTTP transport (JDK HttpServer, virtual threads, stateless)
│   ├── tool       McpTool API, registry, schema builder, typed args
│   └── tools      adapters: validate args → call a service → format the result
├── game     game services: thread-safe server access, commands + policy, inspection, log capture
├── client   (client source set) client services and tools: screenshot, input, chat, worlds
└── mixin    (client source set) one invoker for the vanilla use and attack actions
```

Threading rule: MCP requests run on virtual threads. Every world read or write goes through
`ServerAccess.call(...)` (server thread) or `ClientAccess.call(...)` (render thread), with a timeout.

### Adding a tool (in this mod or in an add-on)

```java
public final class HealTool implements McpTool {
    public ToolDescriptor descriptor() {
        return new ToolDescriptor("heal_player", "Heal player", "Restores a player's health.",
                Schema.object().string("player", "Player name", false).build(), false, false);
    }
    public ToolResult call(ToolArgs args) {
        return ToolResult.json(myService.heal(args.optString("player"))); // the service hops to the server thread
    }
}
// In your initializer:
McpMod.runtime().tools().register(new HealTool());
```

## Building and testing

```bash
./gradlew build
```

This compiles the mod and runs the unit tests (protocol handler, command policy). The jar is written
to `build/libs/`.

## License

Public domain (Unlicense). See [LICENSE](LICENSE).
