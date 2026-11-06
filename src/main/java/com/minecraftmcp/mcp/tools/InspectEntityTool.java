package com.minecraftmcp.mcp.tools;

import com.minecraftmcp.game.inspect.WorldInspectionService;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Detailed view of one entity: attributes, effects, equipment, motion. */
public final class InspectEntityTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"inspect_entity",
			"Inspect entity",
			"Returns details of one loaded entity: summary, rotation, motion, every attribute (base and final "
					+ "value, number of modifiers), active effects and equipment. For raw NBT use run_command with "
					+ "'data get entity <uuid>'.",
			Schema.object()
					.string("id", "Numeric entity id or UUID, as returned by list_entities.", true)
					.build(),
			true, false);

	private final WorldInspectionService inspection;

	public InspectEntityTool(WorldInspectionService inspection) {
		this.inspection = inspection;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		return ToolResult.json(inspection.entity(args.requireString("id")));
	}
}
