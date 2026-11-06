package com.minecraftmcp.client.tools;

import com.minecraftmcp.client.ClientStateService;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Client-side view: open screen, local player, crosshair target and received chat. */
public final class ClientStateTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"client_state",
			"Client state",
			"Returns the client's point of view: whether a world is joined, paused state, FPS, window size, "
					+ "the open screen (type and title), local player position/rotation/held item, what the "
					+ "crosshair targets (block or entity), and chat/system messages received. Pass the last chat "
					+ "'id' you saw as 'chat_after_id' to only get new messages.",
			Schema.object()
					.integer("chat_after_id", "Only chat lines newer than this id. Default 0.", false)
					.integer("chat_limit", "Max chat lines, 0-200. Default 20.", false)
					.build(),
			true, false);

	private final ClientStateService state;

	public ClientStateTool(ClientStateService state) {
		this.state = state;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		return ToolResult.json(state.snapshot(args.intOr("chat_after_id", 0, 0, Integer.MAX_VALUE),
				args.intOr("chat_limit", 20, 0, 200)));
	}
}
