package com.minecraftmcp.mcp.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.minecraftmcp.mcp.protocol.JsonRpc;
import com.minecraftmcp.mcp.protocol.McpProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP "Streamable HTTP" transport, server side, built on the JDK's {@link HttpServer}.
 *
 * <p>Each JSON-RPC request is answered with a plain {@code application/json} body (the spec allows
 * this instead of an SSE stream). The server is stateless: it issues no {@code Mcp-Session-Id}, so
 * clients keep working across world reloads. Requests are processed on virtual threads.
 *
 * <p>Security: binds to loopback by default, rejects browser origins that are not local (DNS
 * rebinding protection required by the spec) and optionally requires a bearer token.
 */
public final class McpHttpServer implements AutoCloseable {

	/** HTTP path of the MCP endpoint. */
	public static final String ENDPOINT = "/mcp";

	private static final Logger LOGGER = LoggerFactory.getLogger("MCP");
	private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

	private final McpProtocolHandler handler;
	private final String host;
	private final int port;
	private final byte[] expectedToken;

	private HttpServer server;
	private ExecutorService executor;

	/**
	 * @param handler   protocol handler
	 * @param host      bind address (use {@code 127.0.0.1} unless you know what you are doing)
	 * @param port      TCP port; {@code 0} picks a free one
	 * @param authToken bearer token required from clients, or empty to disable authentication
	 */
	public McpHttpServer(McpProtocolHandler handler, String host, int port, String authToken) {
		this.handler = handler;
		this.host = host;
		this.port = port;
		this.expectedToken = authToken == null || authToken.isBlank() ? null : authToken.getBytes(StandardCharsets.UTF_8);
	}

	/** Binds the socket and starts serving. */
	public synchronized void start() throws IOException {
		if (server != null) {
			return;
		}
		HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), port), 0);
		executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("minecraft-mcp-http-", 0).factory());
		http.setExecutor(executor);
		http.createContext(ENDPOINT, this::handleMcp);
		http.createContext("/health", this::handleHealth);
		http.start();
		server = http;
		LOGGER.info("MCP server listening on {}", endpointUrl());
	}

	/** Public URL of the MCP endpoint, e.g. {@code http://127.0.0.1:25580/mcp}. */
	public synchronized String endpointUrl() {
		int boundPort = server != null ? server.getAddress().getPort() : port;
		String printableHost = host.contains(":") ? "[" + host + "]" : host;
		return "http://" + printableHost + ":" + boundPort + ENDPOINT;
	}

	public synchronized boolean isRunning() {
		return server != null;
	}

	@Override
	public synchronized void close() {
		if (server == null) {
			return;
		}
		server.stop(0);
		executor.shutdownNow();
		server = null;
		executor = null;
		LOGGER.info("MCP server stopped");
	}

	private void handleHealth(HttpExchange exchange) throws IOException {
		try (exchange) {
			send(exchange, 200, "{\"status\":\"ok\"}");
		}
	}

	private void handleMcp(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!isAllowedOrigin(exchange.getRequestHeaders().getFirst("Origin"))) {
				send(exchange, 403, errorBody(JsonRpc.INVALID_REQUEST, "Forbidden origin."));
				return;
			}
			if (!isAuthorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
				exchange.getResponseHeaders().add("WWW-Authenticate", "Bearer");
				send(exchange, 401, errorBody(JsonRpc.INVALID_REQUEST, "Missing or invalid bearer token."));
				return;
			}
			switch (exchange.getRequestMethod()) {
				case "POST" -> handlePost(exchange);
				case "GET", "DELETE" -> {
					// No server-initiated SSE stream and no sessions to terminate.
					exchange.getResponseHeaders().add("Allow", "POST");
					send(exchange, 405, "");
				}
				default -> {
					exchange.getResponseHeaders().add("Allow", "POST");
					send(exchange, 405, "");
				}
			}
		} catch (RuntimeException e) {
			LOGGER.error("Unhandled error in MCP HTTP handler", e);
			throw e;
		}
	}

	private void handlePost(HttpExchange exchange) throws IOException {
		byte[] body;
		try (InputStream in = exchange.getRequestBody()) {
			body = in.readNBytes(MAX_BODY_BYTES + 1);
		}
		if (body.length > MAX_BODY_BYTES) {
			send(exchange, 413, errorBody(JsonRpc.INVALID_REQUEST, "Request body too large."));
			return;
		}

		JsonElement message;
		try {
			message = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
		} catch (JsonParseException e) {
			send(exchange, 400, errorBody(JsonRpc.PARSE_ERROR, "Invalid JSON: " + e.getMessage()));
			return;
		}

		Optional<JsonObject> response = handler.handle(message);
		if (response.isEmpty()) {
			send(exchange, 202, "");
		} else {
			send(exchange, 200, response.get().toString());
		}
	}

	private boolean isAuthorized(String header) {
		if (expectedToken == null) {
			return true;
		}
		if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return false;
		}
		byte[] provided = header.substring(7).strip().getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(provided, expectedToken);
	}

	private static boolean isAllowedOrigin(String origin) {
		if (origin == null || origin.isBlank() || "null".equals(origin)) {
			return true; // Non-browser clients do not send Origin.
		}
		try {
			String originHost = URI.create(origin).getHost();
			return originHost != null && LOCAL_HOSTS.contains(originHost.toLowerCase());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static String errorBody(int code, String message) {
		return JsonRpc.error(null, code, message).toString();
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 0) {
			exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		}
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
	}
}
