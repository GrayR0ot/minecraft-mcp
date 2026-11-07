package com.minecraftmcp.core;

import com.minecraftmcp.core.config.ConfigLoader;
import com.minecraftmcp.core.config.McpConfig;
import com.minecraftmcp.game.ServerAccess;
import com.minecraftmcp.game.command.CommandPolicy;
import com.minecraftmcp.game.command.CommandService;
import com.minecraftmcp.game.inspect.WorldInspectionService;
import com.minecraftmcp.game.combat.CombatLog;
import com.minecraftmcp.game.log.LogCapture;
import com.minecraftmcp.mcp.protocol.McpProtocolHandler;
import com.minecraftmcp.mcp.protocol.McpServerInfo;
import com.minecraftmcp.mcp.tool.ToolRegistry;
import com.minecraftmcp.mcp.tools.GetPlayerTool;
import com.minecraftmcp.mcp.tools.InspectEntityTool;
import com.minecraftmcp.mcp.tools.InspectItemTool;
import com.minecraftmcp.mcp.tools.ListEntitiesTool;
import com.minecraftmcp.mcp.tools.ReadCombatLogTool;
import com.minecraftmcp.mcp.tools.ReadLogsTool;
import com.minecraftmcp.mcp.tools.RunCommandTool;
import com.minecraftmcp.mcp.tools.ServerStatusTool;
import com.minecraftmcp.mcp.transport.McpHttpServer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Common entrypoint and composition root: loads the config, wires services and tools, then starts
 * the MCP endpoint. Runs on both the client and the dedicated server.
 */
public final class McpMod implements ModInitializer {

	public static final String MOD_ID = "minecraft_mcp";

	private static final Logger LOGGER = LoggerFactory.getLogger("MCP");
	private static final String INSTRUCTIONS = """
			You are connected to a running Minecraft instance (Minecraft MCP mod).
			- Start with server_status. If no world is loaded and client tools exist, use client_world \
			(action=list/create/open) to get into a world.
			- run_command executes any vanilla or modded command and returns its output.
			- get_player, list_entities, inspect_entity and inspect_item (lore, enchantments, components) read the game state.
			- take_screenshot shows what the player sees; client_state gives screen, crosshair target and chat.
			- read_logs with after_id lets you follow new log lines (errors, stack traces) between actions.
			World changes happen on the next game tick: re-read state rather than assuming.""";

	private static McpRuntime runtime;

	@Override
	public void onInitialize() {
		FabricLoader loader = FabricLoader.getInstance();
		McpConfig config = ConfigLoader.load(loader.getConfigDir().resolve(MOD_ID + ".json"), System.getenv());

		LogCapture logs = LogCapture.install(config.logs.bufferSize);
		CombatLog combat = new CombatLog(config.logs.bufferSize);
		combat.registerEvents();

		ServerAccess serverAccess = new ServerAccess(Duration.ofMillis(config.server.gameThreadTimeoutMs));
		serverAccess.registerEvents();

		CommandService commands = new CommandService(serverAccess,
				new CommandPolicy(config.restrictedCommands(), config.commands.denied));
		WorldInspectionService inspection = new WorldInspectionService(serverAccess);

		ToolRegistry tools = new ToolRegistry();
		tools.registerAll(List.of(
				new ServerStatusTool(inspection),
				new RunCommandTool(commands),
				new GetPlayerTool(inspection),
				new ListEntitiesTool(inspection),
				new InspectEntityTool(inspection),
				new InspectItemTool(inspection),
				new ReadLogsTool(logs),
				new ReadCombatLogTool(combat)));

		String version = loader.getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		McpProtocolHandler handler = new McpProtocolHandler(tools,
				new McpServerInfo("minecraft-mcp", "Minecraft MCP", version, INSTRUCTIONS));
		McpHttpServer httpServer = new McpHttpServer(handler, config.server.host, config.server.port, config.server.authToken);

		runtime = new McpRuntime(config, tools, serverAccess, commands, inspection, httpServer);
		McpCommand.register(runtime);

		if (loader.getEnvironmentType() == EnvType.SERVER) {
			ServerLifecycleEvents.SERVER_STOPPED.register(server -> runtime.shutdown());
		}
		// On the client, the endpoint must survive world switches; McpClient stops it on exit.
		runtime.startServer();
		LOGGER.info("Minecraft MCP initialized with {} common tools (command policy: {})", tools.size(),
				config.restrictedCommands() ? "restricted" : "unrestricted");
	}

	/**
	 * The mod runtime, available once the common initializer has run (i.e. from any client
	 * initializer or later). Single static access point bridging Fabric entrypoints.
	 *
	 * @throws IllegalStateException if called before initialization
	 */
	public static McpRuntime runtime() {
		if (runtime == null) {
			throw new IllegalStateException("Minecraft MCP is not initialized yet");
		}
		return runtime;
	}
}
