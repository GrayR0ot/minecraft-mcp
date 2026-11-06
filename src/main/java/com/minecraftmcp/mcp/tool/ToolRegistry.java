package com.minecraftmcp.mcp.tool;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Thread-safe catalogue of the tools exposed by the MCP server.
 *
 * <p>Tools may be registered at any time (the client entrypoint adds client-only tools after the
 * common ones); listing order is alphabetical for stable output.
 */
public final class ToolRegistry {

	private final ConcurrentSkipListMap<String, McpTool> tools = new ConcurrentSkipListMap<>();

	/**
	 * Registers a tool.
	 *
	 * @throws IllegalStateException if a tool with the same name already exists
	 */
	public void register(McpTool tool) {
		McpTool previous = tools.putIfAbsent(tool.name(), tool);
		if (previous != null) {
			throw new IllegalStateException("Duplicate MCP tool name: " + tool.name());
		}
	}

	public void registerAll(Collection<? extends McpTool> toAdd) {
		toAdd.forEach(this::register);
	}

	public Optional<McpTool> find(String name) {
		return Optional.ofNullable(tools.get(name));
	}

	/** Snapshot of the registered tools, sorted by name. */
	public List<McpTool> all() {
		return List.copyOf(tools.values());
	}

	public int size() {
		return tools.size();
	}
}
