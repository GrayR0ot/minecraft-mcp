package com.minecraftmcp.mcp.tools;

import com.minecraftmcp.game.inspect.WorldInspectionService;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Full description of a player: position, health, hunger, inventory, effects... */
public final class GetPlayerTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"get_player",
			"Get player",
			"Returns a player's dimension, position, rotation, game mode, health, food, XP level, selected "
					+ "hotbar slot, held item, full inventory and active effects.",
			Schema.object()
					.string("player", "Player name. Defaults to the first online player (you, in singleplayer).", false)
					.build(),
			true, false);

	private final WorldInspectionService inspection;

	public GetPlayerTool(WorldInspectionService inspection) {
		this.inspection = inspection;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		return ToolResult.json(inspection.player(args.optString("player")));
	}
}
