package com.minecraftmcp.mcp.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Typed, validating view over the {@code arguments} object of a {@code tools/call}.
 *
 * <p>Every accessor throws a {@link ToolException} with an actionable message when the value has
 * the wrong type or is out of range, so tools never deal with raw JSON.
 */
public final class ToolArgs {

	private final JsonObject json;

	public ToolArgs(JsonObject json) {
		this.json = json == null ? new JsonObject() : json;
	}

	/** Raw arguments, for tools that need custom parsing. */
	public JsonObject raw() {
		return json;
	}

	public boolean has(String key) {
		JsonElement element = json.get(key);
		return element != null && !element.isJsonNull();
	}

	public String requireString(String key) {
		return optString(key).orElseThrow(() -> new ToolException("Missing required argument '" + key + "'."));
	}

	public Optional<String> optString(String key) {
		if (!has(key)) {
			return Optional.empty();
		}
		JsonPrimitive primitive = primitive(key);
		if (!primitive.isString()) {
			throw new ToolException("Argument '" + key + "' must be a string.");
		}
		String value = primitive.getAsString().strip();
		return value.isEmpty() ? Optional.empty() : Optional.of(value);
	}

	public OptionalInt optInt(String key, int min, int max) {
		if (!has(key)) {
			return OptionalInt.empty();
		}
		double value = number(key);
		if (value != Math.rint(value)) {
			throw new ToolException("Argument '" + key + "' must be an integer.");
		}
		if (value < min || value > max) {
			throw new ToolException("Argument '" + key + "' must be between " + min + " and " + max + ".");
		}
		return OptionalInt.of((int) value);
	}

	public int intOr(String key, int defaultValue, int min, int max) {
		return optInt(key, min, max).orElse(defaultValue);
	}

	public OptionalDouble optDouble(String key) {
		return has(key) ? OptionalDouble.of(number(key)) : OptionalDouble.empty();
	}

	public double requireDouble(String key) {
		return optDouble(key).orElseThrow(() -> new ToolException("Missing required argument '" + key + "'."));
	}

	public boolean boolOr(String key, boolean defaultValue) {
		if (!has(key)) {
			return defaultValue;
		}
		JsonPrimitive primitive = primitive(key);
		if (!primitive.isBoolean()) {
			throw new ToolException("Argument '" + key + "' must be a boolean.");
		}
		return primitive.getAsBoolean();
	}

	/** Reads a string argument and maps it (case-insensitively) onto an enum constant. */
	public <E extends Enum<E>> Optional<E> optEnum(String key, Class<E> type) {
		return optString(key).map(value -> {
			try {
				return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new ToolException("Argument '" + key + "' has invalid value '" + value + "'. Allowed: "
						+ String.join(", ", Schema.enumValues(type)) + ".");
			}
		});
	}

	private JsonPrimitive primitive(String key) {
		JsonElement element = json.get(key);
		if (!element.isJsonPrimitive()) {
			throw new ToolException("Argument '" + key + "' must be a scalar value.");
		}
		return element.getAsJsonPrimitive();
	}

	private double number(String key) {
		JsonPrimitive primitive = primitive(key);
		if (!primitive.isNumber()) {
			throw new ToolException("Argument '" + key + "' must be a number.");
		}
		double value = primitive.getAsDouble();
		if (!Double.isFinite(value)) {
			throw new ToolException("Argument '" + key + "' must be a finite number.");
		}
		return value;
	}
}
