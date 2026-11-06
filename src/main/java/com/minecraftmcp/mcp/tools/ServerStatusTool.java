package com.minecraftmcp.mcp.tools;

import com.minecraftmcp.game.GameStateException;
import com.minecraftmcp.game.inspect.WorldInspectionService;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reports whether a world is loaded and the server's health (MSPT, tick rate, players...). */
public final class ServerStatusTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"server_status",
			"Server status",
			"Returns whether a world is loaded and, if so, Minecraft version, tick count, average MSPT "
					+ "(milliseconds per tick, above 50 means lag), tick rate, loaded dimensions, online players "
					+ "and day time. Call this first to know the state of the game.",
			Schema.empty(), true, false);

	private final WorldInspectionService inspection;

	public ServerStatusTool(WorldInspectionService inspection) {
		this.inspection = inspection;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		Map<String, Object> status = new LinkedHashMap<>();
		try {
			status.put("server", inspection.serverInfo());
			status.put("worldLoaded", true);
		} catch (GameStateException e) {
			status.put("worldLoaded", false);
			status.put("hint", e.getMessage());
		}
		return ToolResult.json(status);
	}
}
