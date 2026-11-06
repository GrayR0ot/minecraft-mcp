package com.minecraftmcp.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tiny fluent builder for the JSON Schemas of tool inputs.
 *
 * <pre>{@code
 * Schema.object()
 *     .string("command", "Command without leading slash", true)
 *     .integer("radius", "Search radius in blocks", false)
 *     .build();
 * }</pre>
 */
public final class Schema {

	private final JsonObject properties = new JsonObject();
	private final JsonArray required = new JsonArray();

	private Schema() {
	}

	/** Starts an object schema. */
	public static Schema object() {
		return new Schema();
	}

	/** An object schema with no properties. */
	public static JsonObject empty() {
		return object().build();
	}

	public Schema string(String name, String description, boolean isRequired) {
		return property(name, "string", description, isRequired);
	}

	public Schema integer(String name, String description, boolean isRequired) {
		return property(name, "integer", description, isRequired);
	}

	public Schema number(String name, String description, boolean isRequired) {
		return property(name, "number", description, isRequired);
	}

	public Schema bool(String name, String description, boolean isRequired) {
		return property(name, "boolean", description, isRequired);
	}

	/** A string property restricted to the lowercase names of an enum's constants. */
	public <E extends Enum<E>> Schema enumeration(String name, Class<E> type, String description, boolean isRequired) {
		property(name, "string", description, isRequired);
		JsonArray values = new JsonArray();
		enumValues(type).forEach(values::add);
		properties.getAsJsonObject(name).add("enum", values);
		return this;
	}

	public JsonObject build() {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", properties.deepCopy());
		if (!required.isEmpty()) {
			schema.add("required", required.deepCopy());
		}
		schema.addProperty("additionalProperties", false);
		return schema;
	}

	/** Lowercase names of an enum's constants, as accepted by {@link ToolArgs#optEnum}. */
	public static <E extends Enum<E>> List<String> enumValues(Class<E> type) {
		return Arrays.stream(type.getEnumConstants()).map(e -> e.name().toLowerCase(Locale.ROOT)).toList();
	}

	private Schema property(String name, String type, String description, boolean isRequired) {
		JsonObject property = new JsonObject();
		property.addProperty("type", type);
		property.addProperty("description", description);
		properties.add(name, property);
		if (isRequired) {
			required.add(name);
		}
		return this;
	}
}
