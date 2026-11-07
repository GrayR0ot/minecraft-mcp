package com.minecraftmcp.client.world;

import com.minecraftmcp.client.ClientAccess;
import com.minecraftmcp.game.GameStateException;
import com.minecraftmcp.game.ServerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Singleplayer world lifecycle: list, create, open and leave worlds, so the AI can get into a test
 * world without human help.
 */
public final class WorldSessionService {

	/** Maximum time to wait for a world to finish loading or saving. */
	public static final Duration WORLD_TIMEOUT = Duration.ofSeconds(90);

	private static final Pattern WORLD_NAME = Pattern.compile("[A-Za-z0-9 _-]{1,48}");

	private final ClientAccess client;
	private final ServerAccess serverAccess;

	public WorldSessionService(ClientAccess client, ServerAccess serverAccess) {
		this.client = client;
		this.serverAccess = serverAccess;
	}

	/** World generation preset. */
	public enum Preset {
		/** Default terrain. */
		NORMAL,
		/** Superflat in every dimension (vanilla "test world" preset): fast and predictable. */
		FLAT
	}

	/**
	 * A saved world.
	 *
	 * @param id         folder name, used to open it
	 * @param name       display name
	 * @param gameMode   default game mode
	 * @param lastPlayed ISO-8601 timestamp
	 */
	public record WorldEntry(String id, String name, String gameMode, String lastPlayed) {
	}

	/** Current session state after an action. */
	public record SessionState(boolean inWorld, String worldId, String message) {
	}

	public List<WorldEntry> list() {
		LevelStorageSource source = Minecraft.getInstance().getLevelSource();
		try {
			List<LevelSummary> summaries = source.loadLevelSummaries(source.findLevelCandidates()).join();
			return summaries.stream()
					.sorted(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed())
					.map(summary -> new WorldEntry(summary.getLevelId(), summary.getLevelName(),
							summary.getGameMode().getName(), Instant.ofEpochMilli(summary.getLastPlayed()).toString()))
					.toList();
		} catch (LevelStorageException e) {
			throw new GameStateException("Could not list worlds: " + e.getMessage(), e);
		}
	}

	/**
	 * Creates a new world and joins it.
	 *
	 * @param name          world name, also used as folder name
	 * @param preset        terrain preset
	 * @param gameType      initial game mode
	 * @param seed          optional seed (random otherwise)
	 * @param wait          wait until the player is in the world before returning
	 */
	public SessionState create(String name, Preset preset, GameType gameType, OptionalLong seed, boolean wait) {
		if (!WORLD_NAME.matcher(name).matches()) {
			throw new GameStateException("Invalid world name '" + name + "': use 1-48 letters, digits, spaces, '_' or '-'.");
		}
		requireNoWorld();
		if (Minecraft.getInstance().getLevelSource().levelExists(name)) {
			throw new GameStateException("A world folder named '" + name + "' already exists; open it or pick another name.");
		}
		LevelSettings settings = new LevelSettings(name, gameType,
				new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
		WorldOptions options = new WorldOptions(seed.orElseGet(WorldOptions::randomSeed), preset == Preset.NORMAL, false);
		client.post(minecraft -> minecraft.createWorldOpenFlows().createFreshLevel(name, settings, options,
				preset == Preset.FLAT ? WorldPresets::createTestWorldDimensions : WorldPresets::createNormalWorldDimensions,
				new TitleScreen()));
		return wait ? awaitInWorld(name) : new SessionState(false, name, "World creation started.");
	}

	/** Opens an existing world by folder id. */
	public SessionState open(String worldId, boolean wait) {
		requireNoWorld();
		if (!Minecraft.getInstance().getLevelSource().levelExists(worldId)) {
			throw new GameStateException("No world folder '" + worldId + "'. Use action=list to see available worlds.");
		}
		client.post(minecraft -> minecraft.createWorldOpenFlows().openWorld(worldId, () -> minecraft.gui.setScreen(new TitleScreen())));
		return wait ? awaitInWorld(worldId) : new SessionState(false, worldId, "World loading started.");
	}

	/**
	 * Joins a multiplayer server by address (e.g. {@code play.example.com} or {@code localhost:25570}).
	 * The server-side tools do not apply to a remote server; use run_command (sent through the chat),
	 * list_commands and the client tools.
	 */
	public SessionState join(String address, boolean wait) {
		requireNoWorld();
		if (!ServerAddress.isValidAddress(address)) {
			throw new GameStateException("'" + address + "' is not a valid server address (expected host or host:port).");
		}
		ServerAddress parsed = ServerAddress.parseString(address);
		client.post(minecraft -> ConnectScreen.startConnecting(new TitleScreen(), minecraft, parsed,
				new ServerData("Minecraft MCP", address, ServerData.Type.OTHER), false, null));
		if (!wait) {
			return new SessionState(false, address, "Connection started.");
		}
		Minecraft minecraft = Minecraft.getInstance();
		waitUntil(() -> minecraft.player != null && minecraft.level != null, "joining " + address);
		return new SessionState(true, address, "Joined the server.");
	}

	/** Saves and leaves the current world, back to the title screen. */
	public SessionState leave(boolean wait) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return new SessionState(false, null, "Not in a world.");
		}
		client.post(mc -> mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
		if (wait) {
			waitUntil(() -> minecraft.level == null && serverAccess.current().isEmpty(), "leaving the world");
		}
		return new SessionState(false, null, wait ? "Left the world." : "Leaving the world.");
	}

	private void requireNoWorld() {
		if (Minecraft.getInstance().level != null) {
			throw new GameStateException("A world is already loaded. Call client_world with action=leave first.");
		}
	}

	private SessionState awaitInWorld(String worldId) {
		Minecraft minecraft = Minecraft.getInstance();
		waitUntil(() -> minecraft.player != null && minecraft.level != null && serverAccess.current().isPresent(),
				"loading world '" + worldId + "'");
		return new SessionState(true, worldId, "Player is in the world.");
	}

	/**
	 * Polls a condition from the calling (worker) thread. Reads of client fields here are racy but
	 * only null checks, which is acceptable for polling; it avoids blocking on a busy render thread.
	 */
	private static void waitUntil(BooleanSupplier condition, String what) {
		long deadline = System.nanoTime() + WORLD_TIMEOUT.toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new GameStateException("Timed out after " + WORLD_TIMEOUT.toSeconds() + " s while " + what
						+ ". Check client_state and read_logs.");
			}
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new GameStateException("Interrupted while " + what + ".", e);
			}
		}
	}
}
