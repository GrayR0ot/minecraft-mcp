package com.minecraftmcp.game;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Gateway to the running {@link MinecraftServer} (dedicated or integrated).
 *
 * <p>Minecraft is not thread-safe: every read or write of world state goes through
 * {@link #call(Function)}, which runs the task on the server thread and waits for its result with
 * a timeout, so a stuck tick can never hang an MCP request forever.
 */
public final class ServerAccess {

	private final AtomicReference<MinecraftServer> current = new AtomicReference<>();
	private final Duration timeout;

	public ServerAccess(Duration timeout) {
		this.timeout = timeout;
	}

	/** Tracks server start/stop. Call once from the mod initializer. */
	public void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(current::set);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> current.compareAndSet(server, null));
	}

	/** The server if a world is loaded and fully started. */
	public Optional<MinecraftServer> current() {
		return Optional.ofNullable(current.get());
	}

	/**
	 * Runs {@code task} on the server thread and returns its result.
	 *
	 * @throws GameStateException if no world is loaded, the task times out or throws
	 */
	public <T> T call(Function<MinecraftServer, T> task) {
		MinecraftServer server = current().orElseThrow(() -> new GameStateException(
				"No world is loaded. Start or open a world first (client_world tool in singleplayer)."));
		if (server.isSameThread()) {
			return task.apply(server);
		}
		CompletableFuture<T> future = server.submit(() -> task.apply(server));
		try {
			return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			future.cancel(false);
			throw new GameStateException("The server thread did not answer within " + timeout.toMillis()
					+ " ms (server lagging, paused or loading).", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GameStateException("Interrupted while waiting for the server thread.", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new GameStateException("Server task failed: " + cause, cause);
		}
	}

	/**
	 * Resolves a player by name, or the only/first online player when {@code name} is empty.
	 * Must be called on the server thread (inside {@link #call}).
	 */
	public static ServerPlayer resolvePlayer(MinecraftServer server, Optional<String> name) {
		if (name.isPresent()) {
			ServerPlayer player = server.getPlayerList().getPlayerByName(name.get());
			if (player == null) {
				throw new GameStateException("Player '" + name.get() + "' is not online. Online: " + onlineNames(server));
			}
			return player;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			throw new GameStateException("No player is online.");
		}
		return players.getFirst();
	}

	private static List<String> onlineNames(MinecraftServer server) {
		return server.getPlayerList().getPlayers().stream().map(p -> p.getName().getString()).toList();
	}
}
