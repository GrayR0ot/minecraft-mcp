package com.minecraftmcp.mcp.tool;

import com.google.gson.JsonObject;

import java.util.Base64;
import java.util.Objects;

/** A content block of a tool result, as defined by the MCP specification. */
public sealed interface ToolContent {

	/** Serializes this block into its MCP JSON form. */
	JsonObject toJson();

	/** Plain text block. */
	record Text(String text) implements ToolContent {
		public Text {
			Objects.requireNonNull(text, "text");
		}

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("type", "text");
			json.addProperty("text", text);
			return json;
		}
	}

	/** Binary image block (sent base64-encoded). */
	record Image(byte[] data, String mimeType) implements ToolContent {
		public Image {
			Objects.requireNonNull(data, "data");
			Objects.requireNonNull(mimeType, "mimeType");
		}

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("type", "image");
			json.addProperty("data", Base64.getEncoder().encodeToString(data));
			json.addProperty("mimeType", mimeType);
			return json;
		}
	}
}
