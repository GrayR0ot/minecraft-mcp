package com.minecraftmcp.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads {@link McpConfig} from disk, writes back missing keys and applies environment overrides
 * ({@code MCP_TOKEN}, {@code MCP_PORT}) so secrets never need to live in the file.
 */
public final class ConfigLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger("MCP");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private ConfigLoader() {
	}

	/**
	 * Reads the config file, creating it with defaults if absent. Never throws: a broken file is
	 * logged and replaced by defaults in memory (the file itself is left untouched for the user).
	 */
	public static McpConfig load(Path file, Map<String, String> environment) {
		boolean[] readOk = {true};
		McpConfig config = read(file, readOk);
		if (readOk[0]) {
			save(file, config);
		}
		applyEnvironment(config, environment);
		return config;
	}

	/**
	 * Reads the config file as stored (no environment overrides, nothing written back). Used by the
	 * in-game config screen so that environment values are never persisted to disk.
	 */
	public static McpConfig readFile(Path file) {
		return read(file, new boolean[1]);
	}

	private static McpConfig read(Path file, boolean[] readOk) {
		McpConfig config = new McpConfig();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				McpConfig parsed = GSON.fromJson(reader, McpConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			} catch (IOException | JsonParseException e) {
				readOk[0] = false;
				LOGGER.error("Could not read {}, using defaults. Fix or delete the file.", file, e);
			}
		}
		return config.normalize();
	}

	/** Writes the config to disk, logging (not throwing) on failure. */
	public static void save(Path file, McpConfig config) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not write default config to {}", file, e);
		}
	}

	private static void applyEnvironment(McpConfig config, Map<String, String> environment) {
		String token = environment.get("MCP_TOKEN");
		if (token != null && !token.isBlank()) {
			config.server.authToken = token.strip();
		}
		String port = environment.get("MCP_PORT");
		if (port != null && !port.isBlank()) {
			try {
				config.server.port = Integer.parseInt(port.strip());
				config.normalize();
			} catch (NumberFormatException e) {
				LOGGER.warn("Ignoring invalid MCP_PORT value '{}'", port);
			}
		}
	}
}
