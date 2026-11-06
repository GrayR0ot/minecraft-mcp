package com.minecraftmcp.mcp.tools;

import com.minecraftmcp.game.inspect.WorldInspectionService;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolException;
import com.minecraftmcp.mcp.tool.ToolResult;
import com.minecraftmcp.game.inspect.Snapshots.Vec;

import java.util.Optional;

/** Lists entities near a player or a position, nearest first. */
public final class ListEntitiesTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"list_entities",
			"List entities",
			"Lists loaded entities within a radius, nearest first: numeric id, uuid, type, name, position, "
					+ "health and scoreboard tags. Centered on the player unless x, y and z are all given. Use "
					+ "inspect_entity with an id for attributes, effects and equipment.",
			Schema.object()
					.string("player", "Player used as center and dimension. Defaults to the first online player.", false)
					.number("x", "Optional center X (requires y and z).", false)
					.number("y", "Optional center Y.", false)
					.number("z", "Optional center Z.", false)
					.integer("radius", "Search radius in blocks, 1-" + WorldInspectionService.MAX_RADIUS + ". Default 32.", false)
					.string("type", "Entity type filter, e.g. 'zombie' or 'minecraft:zombie'.", false)
					.integer("limit", "Max results, 1-200. Default 50.", false)
					.build(),
			true, false);

	private final WorldInspectionService inspection;

	public ListEntitiesTool(WorldInspectionService inspection) {
		this.inspection = inspection;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		Optional<Vec> center = Optional.empty();
		int coordinates = (args.has("x") ? 1 : 0) + (args.has("y") ? 1 : 0) + (args.has("z") ? 1 : 0);
		if (coordinates == 3) {
			center = Optional.of(new Vec(args.requireDouble("x"), args.requireDouble("y"), args.requireDouble("z")));
		} else if (coordinates != 0) {
			throw new ToolException("Give all of x, y and z, or none of them.");
		}
		return ToolResult.json(inspection.entitiesAround(
				center,
				args.optString("player"),
				args.intOr("radius", 32, 1, WorldInspectionService.MAX_RADIUS),
				args.optString("type"),
				args.intOr("limit", 50, 1, 200)));
	}
}
