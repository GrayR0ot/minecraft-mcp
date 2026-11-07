package com.minecraftmcp.core;

import com.minecraftmcp.core.config.McpConfig;
import com.minecraftmcp.game.ServerAccess;
import com.minecraftmcp.game.command.CommandService;
import com.minecraftmcp.game.inspect.WorldInspectionService;
import com.minecraftmcp.mcp.tool.ToolRegistry;
import com.minecraftmcp.mcp.transport.McpHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Object graph of the mod, built once by {@link McpMod} (the composition root).
 *
 * <p>This is the integration point for the client entrypoint and for add-on mods: register extra
 * MCP tools through {@link #tools()} at initialization time.
 */
public final class McpRuntime {

	private static final Logger LOGGER = LoggerFactory.getLogger("MCP");

	private final McpConfig config;
	private final ToolRegistry tools;
	private final ServerAccess serverAccess;
	private final CommandService commands;
	private final WorldInspectionService inspection;
	private final McpHttpServer httpServer;

	McpRuntime(McpConfig config, ToolRegistry tools, ServerAccess serverAccess, CommandService commands,
			   WorldInspectionService inspection,
			   McpHttpServer httpServer) {
		this.config = config;
		this.tools = tools;
		this.serverAccess = serverAccess;
		this.commands = commands;
		this.inspection = inspection;
		this.httpServer = httpServer;
	}

	public McpConfig config() {
		return config;
	}

	/** Registry of MCP tools; add-ons may register their own tools here. */
	public ToolRegistry tools() {
		return tools;
	}

	/** World inspection service; the client side plugs in remote-server support here. */
	public WorldInspectionService inspection() {
		return inspection;
	}

	/** Command execution service; the client side plugs in remote-server support here. */
	public CommandService commands() {
		return commands;
	}

	public ServerAccess serverAccess() {
		return serverAccess;
	}

	/** URL of the MCP endpoint if the server is running. */
	public Optional<String> endpointUrl() {
		return httpServer.isRunning() ? Optional.of(httpServer.endpointUrl()) : Optional.empty();
	}

	/** Starts the HTTP endpoint unless disabled or unsafe. Never throws. */
	void startServer() {
		if (!config.server.enabled) {
			LOGGER.info("MCP server disabled in config (server.enabled = false)");
			return;
		}
		if (!isLoopback(config.server.host) && config.server.authToken.isBlank()) {
			LOGGER.error("Refusing to expose the MCP server on non-loopback address '{}' without an auth token. "
					+ "Set server.authToken (or MCP_TOKEN) or bind to 127.0.0.1.", config.server.host);
			return;
		}
		try {
			httpServer.start();
		} catch (IOException e) {
			LOGGER.error("Could not start the MCP server on {}:{} (port already in use?). "
					+ "Change server.port in config/minecraft_mcp.json or set MCP_PORT.", config.server.host, config.server.port, e);
		}
	}

	/** Stops the HTTP endpoint. Safe to call several times. */
	public void shutdown() {
		httpServer.close();
	}

	private static boolean isLoopback(String host) {
		try {
			return InetAddress.getByName(host).isLoopbackAddress();
		} catch (UnknownHostException e) {
			return false;
		}
	}
}
