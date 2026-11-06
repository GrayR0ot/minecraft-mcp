package com.minecraftmcp.mcp.tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Result of a {@code tools/call}.
 *
 * @param content blocks returned to the client, in order
 * @param isError {@code true} if the tool failed; the client (the AI) sees the content as an error
 *                message it can react to, unlike JSON-RPC protocol errors
 */
public record ToolResult(List<ToolContent> content, boolean isError) {

	/** Shared pretty-printer used to render structured data as readable text. */
	public static final Gson JSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.serializeSpecialFloatingPointValues()
			.create();

	public ToolResult {
		content = List.copyOf(content);
	}

	/** Successful result with a single text block. */
	public static ToolResult text(String text) {
		return new ToolResult(List.of(new ToolContent.Text(text)), false);
	}

	/** Successful result rendering {@code data} (record, map, list, JsonElement...) as pretty JSON. */
	public static ToolResult json(Object data) {
		return text(JSON.toJson(data));
	}

	/** Failed result with a single explanatory text block. */
	public static ToolResult error(String message) {
		return new ToolResult(List.of(new ToolContent.Text(message)), true);
	}

	/** Serializes this result into the MCP {@code CallToolResult} JSON form. */
	public JsonObject toJson() {
		JsonArray blocks = new JsonArray();
		content.forEach(block -> blocks.add(block.toJson()));
		JsonObject json = new JsonObject();
		json.add("content", blocks);
		json.addProperty("isError", isError);
		return json;
	}
}
