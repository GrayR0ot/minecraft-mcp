package com.minecraftmcp.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecraftmcp.mcp.protocol.JsonRpc;
import com.minecraftmcp.mcp.protocol.McpProtocolHandler;
import com.minecraftmcp.mcp.protocol.McpServerInfo;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolException;
import com.minecraftmcp.mcp.tool.ToolRegistry;
import com.minecraftmcp.mcp.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpProtocolHandlerTest {

	private McpProtocolHandler handler;

	@BeforeEach
	void setUp() {
		ToolRegistry registry = new ToolRegistry();
		registry.register(new EchoTool());
		handler = new McpProtocolHandler(registry, new McpServerInfo("test", "Test", "1.0", "hello"));
	}

	@Test
	void initializeNegotiatesRequestedVersion() {
		JsonObject result = call("initialize", "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{}}").getAsJsonObject("result");
		assertEquals("2025-06-18", result.get("protocolVersion").getAsString());
		assertTrue(result.getAsJsonObject("capabilities").has("tools"));
		assertEquals("test", result.getAsJsonObject("serverInfo").get("name").getAsString());
	}

	@Test
	void initializeFallsBackToLatestForUnknownVersion() {
		JsonObject result = call("initialize", "{\"protocolVersion\":\"1999-01-01\"}").getAsJsonObject("result");
		assertEquals(McpProtocolHandler.SUPPORTED_VERSIONS.getFirst(), result.get("protocolVersion").getAsString());
	}

	@Test
	void notificationsGetNoResponse() {
		assertTrue(handler.handle(JsonParser.parseString("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).isEmpty());
	}

	@Test
	void listsTools() {
		JsonObject tool = call("tools/list", "{}").getAsJsonObject("result").getAsJsonArray("tools").get(0).getAsJsonObject();
		assertEquals("echo", tool.get("name").getAsString());
		assertEquals("object", tool.getAsJsonObject("inputSchema").get("type").getAsString());
	}

	@Test
	void callsTool() {
		JsonObject result = call("tools/call", "{\"name\":\"echo\",\"arguments\":{\"text\":\"hi\"}}").getAsJsonObject("result");
		assertFalse(result.get("isError").getAsBoolean());
		assertEquals("hi", result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
	}

	@Test
	void toolExceptionBecomesErrorResult() {
		JsonObject result = call("tools/call", "{\"name\":\"echo\",\"arguments\":{}}").getAsJsonObject("result");
		assertTrue(result.get("isError").getAsBoolean());
	}

	@Test
	void unknownToolIsProtocolError() {
		JsonObject response = call("tools/call", "{\"name\":\"nope\"}");
		assertEquals(JsonRpc.INVALID_PARAMS, response.getAsJsonObject("error").get("code").getAsInt());
	}

	@Test
	void unknownMethodIsProtocolError() {
		JsonObject response = call("resources/list", "{}");
		assertEquals(JsonRpc.METHOD_NOT_FOUND, response.getAsJsonObject("error").get("code").getAsInt());
	}

	@Test
	void rejectsBatches() {
		JsonObject response = handler.handle(JsonParser.parseString("[]")).orElseThrow();
		assertEquals(JsonRpc.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
	}

	private JsonObject call(String method, String params) {
		String message = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"" + method + "\",\"params\":" + params + "}";
		JsonObject response = handler.handle(JsonParser.parseString(message)).orElseThrow();
		assertEquals(7, response.get("id").getAsInt());
		return response;
	}

	private static final class EchoTool implements McpTool {
		@Override
		public ToolDescriptor descriptor() {
			return new ToolDescriptor("echo", "Echo", "Echoes text.",
					Schema.object().string("text", "Text to echo", true).build(), true, false);
		}

		@Override
		public ToolResult call(ToolArgs args) throws ToolException {
			return ToolResult.text(args.requireString("text"));
		}
	}
}
