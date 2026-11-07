package com.minecraftmcp.mcp.tools;

import com.minecraftmcp.game.inspect.WorldInspectionService;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Full details of one inventory stack: name, lore, enchantments, durability, tooltip, all components. */
public final class InspectItemTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"inspect_item",
			"Inspect item",
			"Returns everything about one item stack of a player: name, lore, enchantments, durability, the "
					+ "tooltip lines as the player sees them (advanced tooltips on), and the whole stack serialized "
					+ "with every data component (custom data, attribute modifiers, custom model data...).",
			Schema.object()
					.string("player", "Player name. Defaults to the first online player.", false)
					.integer("slot", "Inventory slot: 0-8 hotbar, 9-35 storage, 36-39 boots/leggings/chestplate/helmet, "
							+ "40 offhand. Defaults to the selected hotbar slot.", false)
					.build(),
			true, false);

	private final WorldInspectionService inspection;

	public InspectItemTool(WorldInspectionService inspection) {
		this.inspection = inspection;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		return ToolResult.json(inspection.itemDetails(args.optString("player"), args.optInt("slot", 0, 40)));
	}
}
