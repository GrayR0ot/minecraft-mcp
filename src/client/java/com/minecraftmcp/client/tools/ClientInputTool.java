package com.minecraftmcp.client.tools;

import com.minecraftmcp.client.input.InputService;
import com.minecraftmcp.client.input.InputService.Action;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolException;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Drives the local player like a human would: look, hotbar, clicks, chat. */
public final class ClientInputTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"client_input",
			"Client input",
			"Simulates one input of the local player through vanilla code paths. Actions: 'look' (yaw, pitch "
					+ "in degrees; yaw 0 = south, 90 = west, pitch -90 = up), 'select_slot' (slot 0-8), 'use' "
					+ "(right click on what the crosshair targets, or use the held item), 'attack' (left click), "
					+ "'close_screen', 'chat' (text; a leading '/' sends it as a command with the player's own "
					+ "permissions), 'hold' (hold 'key' for 'ticks', 20 ticks = 1 s: draw a bow with use/20, "
						+ "eat with use/35, walk with forward, jump, sneak, sprint; returns once released), 'respawn' "
						+ "(after death).",
			Schema.object()
					.enumeration("action", Action.class, "Input to perform.", true)
					.number("yaw", "For 'look': horizontal angle in degrees.", false)
					.number("pitch", "For 'look': vertical angle in degrees, -90 (up) to 90 (down).", false)
					.integer("slot", "For 'select_slot': hotbar slot 0-8.", false)
					.string("text", "For 'chat': message or /command.", false)
						.enumeration("key", InputService.HeldKey.class, "For 'hold': key to hold down.", false)
						.integer("ticks", "For 'hold': duration in ticks, 1-" + InputService.MAX_HOLD_TICKS + ".", false)
						.bool("wait", "For 'hold': wait for the release before returning (default true). Use false "
								+ "to keep aiming with 'look' while a bow is drawn.", false)
					.build(),
			false, false);

	private final InputService input;

	public ClientInputTool(InputService input) {
		this.input = input;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		Action action = args.optEnum("action", Action.class)
				.orElseThrow(() -> new ToolException("Missing required argument 'action'."));
		if (action == Action.LOOK && (!args.has("yaw") || !args.has("pitch"))) {
			throw new ToolException("Action 'look' needs both 'yaw' and 'pitch'.");
		}
		if (action == Action.SELECT_SLOT && !args.has("slot")) {
			throw new ToolException("Action 'select_slot' needs 'slot'.");
		}
		InputService.Request request = new InputService.Request(
				action,
				(float) args.optDouble("yaw").orElse(0),
				(float) args.optDouble("pitch").orElse(0),
				args.intOr("slot", 0, 0, 8),
				args.optString("text").orElse(null),
				args.optEnum("key", InputService.HeldKey.class).orElse(null),
				args.intOr("ticks", 20, 1, InputService.MAX_HOLD_TICKS),
				args.boolOr("wait", true));
		if (action == Action.HOLD && request.key() == null) {
			throw new ToolException("Action 'hold' needs 'key'.");
		}
		return ToolResult.text(input.perform(request));
	}
}
