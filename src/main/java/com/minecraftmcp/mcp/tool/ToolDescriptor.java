package com.minecraftmcp.mcp.tool;

import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Metadata of an {@link McpTool}, serialized as an entry of the {@code tools/list} response.
 *
 * @param name        unique machine name ({@code [a-z0-9_]+}), e.g. {@code run_command}
 * @param title       short human-readable title
 * @param description what the tool does and when to use it; this is the AI's only documentation
 * @param inputSchema JSON Schema of the {@code arguments} object (see {@link Schema})
 * @param readOnly    {@code true} if the tool never modifies the game state
 * @param destructive {@code true} if the tool may perform hard-to-undo changes
 */
public record ToolDescriptor(String name, String title, String description, JsonObject inputSchema,
							 boolean readOnly, boolean destructive) {

	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

	public ToolDescriptor {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(inputSchema, "inputSchema");
		if (!NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("Invalid tool name '" + name + "', expected [a-z0-9_]{1,64}");
		}
	}

	/** Builds the JSON object listed in {@code tools/list}. */
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("name", name);
		json.addProperty("title", title);
		json.addProperty("description", description);
		json.add("inputSchema", inputSchema.deepCopy());

		JsonObject annotations = new JsonObject();
		annotations.addProperty("title", title);
		annotations.addProperty("readOnlyHint", readOnly);
		annotations.addProperty("destructiveHint", destructive);
		annotations.addProperty("openWorldHint", false);
		json.add("annotations", annotations);
		return json;
	}
}
