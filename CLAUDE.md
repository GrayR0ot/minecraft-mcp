# CLAUDE.md - Development Guide for the Minecraft MCP Fabric Mod

## 🛠️ Tech Stack & Environment
* **Language:** Java 25 (records, pattern matching, virtual threads where relevant).
* **Loader framework:** Fabric Loader 0.20.1 + Fabric API, **Minecraft 26.3** (unobfuscated code, official Mojang names: `ServerLevel`, `Identifier`, `Component`…; no more Yarn).
* **Build tools:** Gradle 9.8 (wrapper) + Loom `net.fabricmc.fabric-loom` 1.19, separate `main` / `client` source sets.
* **Protocols:** JSON-RPC 2.0 / MCP (Streamable HTTP transport, `http://127.0.0.1:25580/mcp`).
* **Local build (Windows):** `JAVA_HOME="C:\Users\<you>\.jdks\temurin-25.0.1"` then `./gradlew build` (tests included) or `./gradlew runClient`.

---

## 📂 Project Structure & Architecture
* `com.minecraftmcp.core`: mod initialization (composition root `McpMod`), runtime, configuration, `/mcp` command.
* `com.minecraftmcp.mcp`: protocol layer only (`protocol`, `transport`, `tool` = tool API, `tools` = server-side adapters).
* `com.minecraftmcp.game`: technical game services (thread-safe server access, commands + policy, inspection, log capture, combat log).
* `com.minecraftmcp.client` (`client` source set): client services and tools (screenshots, input, chat, worlds).
* `com.minecraftmcp.mixin.client` (`client` source set): client mixins. No common mixin for now (`mixin.common` if needed).

---

## 💡 Best Practices & Clean Code Rules
1. **Thread safety:** Minecraft is not thread-safe. Every access to world state goes through `ServerAccess.call(...)` (server thread) or `ClientAccess.call(...)` (render thread), both of which enforce a timeout. MCP requests run on virtual threads.
2. **Mixins:** Keep mixins to the strict minimum. Document each one and use `@Unique` or explicit prefixes (`mcpMod$`) to avoid clashes with other mods.
3. **MCP / business separation:** MCP tools (`mcp.tools`, `client.tools`) contain no game logic: they validate arguments, call a service (`game`, `client.*`) and format the result. No `net.minecraft` imports in `mcp.protocol` / `mcp.transport` / `mcp.tool`.
4. **Error handling:** No empty `catch (Exception e)` blocks. Log properly via SLF4J (`LoggerFactory.getLogger("MCP")`). Expected errors reach the AI through `ToolException` / `GameStateException` (actionable message).
5. **i18n:** All in-game text goes through `Component.translatable` with a key in `en_us.json`.

---

## 🤖 Autonomy Guidelines for Claude
* **In-game test loop:** run `./gradlew runClient` in the background, wait for `Minecraft MCP client tools registered` in the output, then use the `minecraft` MCP server (`.mcp.json`): `server_status` → `client_world` (create, preset `flat`) → actions → `take_screenshot` / `read_logs`. The game keeps running while you think: shelter the player (or `/tick freeze`) between action sequences.
* **Reason first:** On a bug or exception, analyze the logs first (`read_logs` with `min_level: ERROR`, or `run/logs/latest.log`) and the stack trace yourself. Form a technical hypothesis and fix the code directly.
* **Verify Minecraft APIs:** names change often between versions; inspect classes with `javap` on `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-*-deobf/26.3/*.jar` rather than guessing.
* **AI safety:** `run_command` goes through `CommandPolicy` (restricted mode by default, including behind `/execute … run`). Do not weaken this guard rail without an explicit request.
* **Open source quality:** Write clear commit messages (conventional commits: `feat:`, `fix:`, `refactor:`), document public classes and keep the code easy to fork and contribute to.
