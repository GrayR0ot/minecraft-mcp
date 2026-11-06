package com.minecraftmcp.mcp.tools;

import com.minecraftmcp.game.log.LogCapture;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Reads the in-memory game log with a cursor and filters. */
public final class ReadLogsTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"read_logs",
			"Read logs",
			"Reads recent game log entries (same content as latest.log, including stack traces) from an "
					+ "in-memory buffer. Pass the returned 'nextId' as 'after_id' on the next call to only get new "
					+ "entries. Filter by minimum level, logger name or text to find errors quickly.",
			Schema.object()
					.integer("after_id", "Only entries newer than this cursor. Default 0 (whole buffer).", false)
					.string("min_level", "Minimum level: TRACE, DEBUG, INFO, WARN, ERROR or FATAL.", false)
					.string("contains", "Case-insensitive text to search in messages and stack traces.", false)
					.string("logger", "Case-insensitive substring of the logger name, e.g. 'MCP'.", false)
					.integer("limit", "Max entries (newest kept), 1-500. Default 100.", false)
					.build(),
			true, false);

	private final LogCapture logs;

	public ReadLogsTool(LogCapture logs) {
		this.logs = logs;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		return ToolResult.json(logs.query(
				args.intOr("after_id", 0, 0, Integer.MAX_VALUE),
				args.optString("min_level"),
				args.optString("contains"),
				args.optString("logger"),
				args.intOr("limit", 100, 1, 500)));
	}
}
