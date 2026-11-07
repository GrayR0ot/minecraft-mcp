package com.minecraftmcp.client.tools;

import com.minecraftmcp.client.container.ContainerService;
import com.minecraftmcp.client.container.ContainerService.Click;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolException;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Uses inventories and menus like a player: open, list slots, hover (tooltip), click. */
public final class ClientContainerTool implements McpTool {

	/** Actions of this tool. */
	public enum ContainerAction { OPEN_INVENTORY, SLOTS, HOVER, CLICK }

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"client_container",
			"Client container",
			"Uses inventories, chests and plugin menus like a player, without a mouse. 'open_inventory' opens the "
					+ "player inventory (E). 'slots' lists the non-empty slots of the open container screen (index, item, "
					+ "name, lore) and its title. 'hover' moves the cursor over a slot so the game draws its tooltip: call "
					+ "take_screenshot right after to see the tooltip exactly as a player does (colors, lore, layout); the "
					+ "result also returns the tooltip text with &-color codes. 'click' clicks a slot (left, right, shift, "
					+ "middle, drop, drop_stack, swap with a hotbar slot) through vanilla's container packets. Slot indexes "
					+ "are the container's: use 'slots' to find them. Close with client_input action=close_screen.",
			Schema.object()
					.enumeration("action", ContainerAction.class, "What to do.", true)
					.integer("slot", "For hover/click: container slot index (-999 with click = outside the window).", false)
					.enumeration("click", Click.class, "For click: kind of click. Default left.", false)
					.integer("hotbar", "For click=swap: hotbar slot 0-8.", false)
					.build(),
			false, false);

	private final ContainerService containers;

	public ClientContainerTool(ContainerService containers) {
		this.containers = containers;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		ContainerAction action = args.optEnum("action", ContainerAction.class)
				.orElseThrow(() -> new ToolException("Missing required argument 'action'."));
		return switch (action) {
			case OPEN_INVENTORY -> ToolResult.json(containers.openInventory());
			case SLOTS -> ToolResult.json(containers.state());
			case HOVER -> ToolResult.json(containers.hover(requireSlot(args)));
			case CLICK -> ToolResult.json(containers.click(requireSlot(args),
					args.optEnum("click", Click.class).orElse(Click.LEFT), args.intOr("hotbar", 0, 0, 8)));
		};
	}

	private static int requireSlot(ToolArgs args) {
		return args.optInt("slot", -999, 10_000)
				.orElseThrow(() -> new ToolException("Missing required argument 'slot'."));
	}
}
