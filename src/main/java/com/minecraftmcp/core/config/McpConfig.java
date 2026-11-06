package com.minecraftmcp.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * User configuration, stored in {@code config/minecraft_mcp.json}.
 *
 * <p>Plain mutable holder for Gson: field initializers are the defaults and survive missing keys.
 * Call {@link #normalize()} after loading; treat the instance as read-only afterwards.
 */
public final class McpConfig {

	public Server server = new Server();
	public Commands commands = new Commands();
	public Logs logs = new Logs();
	public Client client = new Client();

	/** MCP HTTP endpoint. */
	public static final class Server {
		/** Set to false to disable the MCP server entirely. */
		public boolean enabled = true;
		/** Bind address. Keep loopback unless you also set a token. */
		public String host = "127.0.0.1";
		public int port = 25580;
		/** Optional bearer token. Overridden by the MCP_TOKEN environment variable. */
		public String authToken = "";
		/** Maximum time a tool may wait for the game thread, in milliseconds. */
		public int gameThreadTimeoutMs = 10_000;
	}

	/** Safety net for the {@code run_command} tool. */
	public static final class Commands {
		/** RESTRICTED blocks the commands in {@link #denied}; UNRESTRICTED allows everything. */
		public String mode = "RESTRICTED";
		/** Root command names refused in RESTRICTED mode (also when nested in /execute ... run). */
		public List<String> denied = new ArrayList<>(List.of(
				"stop", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "whitelist",
				"kick", "save-off", "publish", "transfer", "jfr", "perf"));
	}

	/** In-memory log capture for the {@code read_logs} tool. */
	public static final class Logs {
		public int bufferSize = 2_000;
	}

	/** Client-only behaviour. */
	public static final class Client {
		/** Keeps the game running when its window loses focus, so the AI can drive it in background. */
		public boolean disablePauseOnLostFocus = true;
		/** Screenshots wider than this are downscaled (by an integer factor) to save tokens. */
		public int screenshotMaxWidth = 960;
		public int chatBufferSize = 200;
	}

	/** Whether the command policy blocks denied commands. */
	public boolean restrictedCommands() {
		return !"UNRESTRICTED".equals(commands.mode);
	}

	/** Fixes missing sections and clamps out-of-range values. */
	public McpConfig normalize() {
		if (server == null) server = new Server();
		if (commands == null) commands = new Commands();
		if (logs == null) logs = new Logs();
		if (client == null) client = new Client();

		if (server.host == null || server.host.isBlank()) server.host = "127.0.0.1";
		if (server.authToken == null) server.authToken = "";
		server.port = clamp(server.port, 0, 65_535);
		server.gameThreadTimeoutMs = clamp(server.gameThreadTimeoutMs, 500, 120_000);

		commands.mode = commands.mode == null ? "RESTRICTED" : commands.mode.toUpperCase(Locale.ROOT);
		if (commands.denied == null) commands.denied = new ArrayList<>();

		logs.bufferSize = clamp(logs.bufferSize, 100, 50_000);
		client.screenshotMaxWidth = clamp(client.screenshotMaxWidth, 128, 4096);
		client.chatBufferSize = clamp(client.chatBufferSize, 10, 5_000);

		return this;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
