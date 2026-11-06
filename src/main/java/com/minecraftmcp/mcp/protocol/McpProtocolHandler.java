package com.minecraftmcp.mcp.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolException;
import com.minecraftmcp.mcp.tool.ToolRegistry;
import com.minecraftmcp.mcp.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Transport-independent MCP message handler (server side).
 *
 * <p>Implements the subset of the protocol this server needs: lifecycle ({@code initialize},
 * {@code notifications/initialized}, {@code ping}) and tools ({@code tools/list},
 * {@code tools/call}). The handler is stateless and thread-safe; it never touches Minecraft.
 */
public final class McpProtocolHandler {

	/** Protocol revisions we can speak, newest first. */
	public static final List<String> SUPPORTED_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");

	private static final Logger LOGGER = LoggerFactory.getLogger("MCP");

	private final ToolRegistry tools;
	private final McpServerInfo serverInfo;

	public McpProtocolHandler(ToolRegistry tools, McpServerInfo serverInfo) {
		this.tools = tools;
		this.serverInfo = serverInfo;
	}

	/**
	 * Handles one incoming JSON-RPC message.
	 *
	 * @return the response to send, or empty for notifications and client responses
	 */
	public Optional<JsonObject> handle(JsonElement message) {
		if (message == null || !message.isJsonObject()) {
			return Optional.of(JsonRpc.error(null, JsonRpc.INVALID_REQUEST, "Expected a single JSON-RPC object (batches are not supported)."));
		}
		JsonObject object = message.getAsJsonObject();
		if (!JsonRpc.VERSION.equals(stringOrNull(object, "jsonrpc"))) {
			return Optional.of(JsonRpc.error(object.get("id"), JsonRpc.INVALID_REQUEST, "Missing or invalid 'jsonrpc' field (expected \"2.0\")."));
		}

		String method = stringOrNull(object, "method");
		JsonElement id = object.get("id");
		boolean isNotification = id == null || id.isJsonNull();

		if (method == null) {
			// A response to a server->client request; we never send any, so just ignore it.
			return Optional.empty();
		}
		JsonObject params = object.has("params") && object.get("params").isJsonObject()
				? object.getAsJsonObject("params") : new JsonObject();

		if (isNotification) {
			LOGGER.debug("MCP notification received: {}", method);
			return Optional.empty();
		}

		try {
			JsonElement result = switch (method) {
				case "initialize" -> initialize(params);
				case "ping" -> new JsonObject();
				case "tools/list" -> listTools();
				case "tools/call" -> callTool(params);
				default -> throw new RpcException(JsonRpc.METHOD_NOT_FOUND, "Method not found: " + method);
			};
			return Optional.of(JsonRpc.result(id, result));
		} catch (RpcException e) {
			return Optional.of(JsonRpc.error(id, e.code, e.getMessage()));
		} catch (RuntimeException e) {
			LOGGER.error("Unexpected error while handling MCP method '{}'", method, e);
			return Optional.of(JsonRpc.error(id, JsonRpc.INTERNAL_ERROR, "Internal error: " + e));
		}
	}

	private JsonObject initialize(JsonObject params) {
		String requested = stringOrNull(params, "protocolVersion");
		String negotiated = requested != null && SUPPORTED_VERSIONS.contains(requested) ? requested : SUPPORTED_VERSIONS.getFirst();

		JsonObject toolsCapability = new JsonObject();
		toolsCapability.addProperty("listChanged", false);
		JsonObject capabilities = new JsonObject();
		capabilities.add("tools", toolsCapability);

		JsonObject info = new JsonObject();
		info.addProperty("name", serverInfo.name());
		info.addProperty("title", serverInfo.title());
		info.addProperty("version", serverInfo.version());

		JsonObject result = new JsonObject();
		result.addProperty("protocolVersion", negotiated);
		result.add("capabilities", capabilities);
		result.add("serverInfo", info);
		result.addProperty("instructions", serverInfo.instructions());
		LOGGER.info("MCP client initialized (protocol {}, client: {})", negotiated,
				params.has("clientInfo") ? params.get("clientInfo") : "unknown");
		return result;
	}

	private JsonObject listTools() {
		JsonArray list = new JsonArray();
		tools.all().forEach(tool -> list.add(tool.descriptor().toJson()));
		JsonObject result = new JsonObject();
		result.add("tools", list);
		return result;
	}

	private JsonObject callTool(JsonObject params) {
		String name = stringOrNull(params, "name");
		if (name == null) {
			throw new RpcException(JsonRpc.INVALID_PARAMS, "Missing tool 'name'.");
		}
		McpTool tool = tools.find(name)
				.orElseThrow(() -> new RpcException(JsonRpc.INVALID_PARAMS, "Unknown tool: " + name));
		JsonElement rawArgs = params.get("arguments");
		if (rawArgs != null && !rawArgs.isJsonNull() && !rawArgs.isJsonObject()) {
			throw new RpcException(JsonRpc.INVALID_PARAMS, "'arguments' must be an object.");
		}
		JsonObject arguments = rawArgs != null && rawArgs.isJsonObject() ? rawArgs.getAsJsonObject() : new JsonObject();

		long start = System.nanoTime();
		ToolResult result;
		try {
			result = tool.call(new ToolArgs(arguments));
		} catch (ToolException e) {
			result = ToolResult.error(e.getMessage());
		} catch (RuntimeException e) {
			// Game-side failures (no world, timeout...) carry user-facing messages too.
			LOGGER.warn("MCP tool '{}' failed", name, e);
			result = ToolResult.error("Tool '" + name + "' failed: " + e.getMessage());
		}
		LOGGER.debug("MCP tool '{}' finished in {} ms (error={})", name, (System.nanoTime() - start) / 1_000_000, result.isError());
		return result.toJson();
	}

	private static String stringOrNull(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
				? element.getAsString() : null;
	}

	/** Protocol-level failure mapped onto a JSON-RPC error object. */
	private static final class RpcException extends RuntimeException {
		private final int code;

		RpcException(int code, String message) {
			super(message);
			this.code = code;
		}
	}
}
