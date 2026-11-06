package com.minecraftmcp.mcp.tool;

import com.google.gson.JsonObject;

/**
 * A single capability exposed to MCP clients through {@code tools/list} and {@code tools/call}.
 *
 * <p>Implementations are adapters: they validate arguments, delegate to a game service and
 * format the outcome. They must not contain game logic themselves.
 *
 * <p>{@link #call(ToolArgs)} is invoked on an HTTP worker thread (a virtual thread), never on the
 * Minecraft server or render thread. Implementations must hop to the right thread through the
 * services they call.
 */
public interface McpTool {

	/** Static metadata advertised to clients. Must be cheap and always return the same value. */
	ToolDescriptor descriptor();

	/**
	 * Executes the tool.
	 *
	 * @param args the {@code arguments} object sent by the client, wrapped for typed access
	 * @return the result to send back; use {@link ToolResult#error(String)} for expected failures
	 * @throws ToolException for invalid arguments or expected failures (reported as {@code isError})
	 */
	ToolResult call(ToolArgs args) throws ToolException;

	/** Convenience accessor for {@code descriptor().name()}. */
	default String name() {
		return descriptor().name();
	}

	/** Raw access to a schema object, mainly for tests. */
	default JsonObject inputSchema() {
		return descriptor().inputSchema();
	}
}
