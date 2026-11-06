package com.minecraftmcp.mcp.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** JSON-RPC 2.0 constants and message builders. */
public final class JsonRpc {

	public static final String VERSION = "2.0";

	public static final int PARSE_ERROR = -32700;
	public static final int INVALID_REQUEST = -32600;
	public static final int METHOD_NOT_FOUND = -32601;
	public static final int INVALID_PARAMS = -32602;
	public static final int INTERNAL_ERROR = -32603;

	private JsonRpc() {
	}

	/** Builds a success response. */
	public static JsonObject result(JsonElement id, JsonElement result) {
		JsonObject response = envelope(id);
		response.add("result", result);
		return response;
	}

	/** Builds an error response; {@code id} may be {@code null} when the request id is unknown. */
	public static JsonObject error(JsonElement id, int code, String message) {
		JsonObject error = new JsonObject();
		error.addProperty("code", code);
		error.addProperty("message", message);
		JsonObject response = envelope(id);
		response.add("error", error);
		return response;
	}

	private static JsonObject envelope(JsonElement id) {
		JsonObject response = new JsonObject();
		response.addProperty("jsonrpc", VERSION);
		response.add("id", id == null ? JsonNull.INSTANCE : id);
		return response;
	}
}
