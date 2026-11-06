package com.minecraftmcp.client;

import com.minecraftmcp.game.GameStateException;
import net.minecraft.client.Minecraft;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Gateway to the client (render) thread, the client-side twin of
 * {@link com.minecraftmcp.game.ServerAccess}.
 */
public final class ClientAccess {

	private final Duration timeout;

	public ClientAccess(Duration timeout) {
		this.timeout = timeout;
	}

	/** Runs {@code task} on the render thread and waits for its result. */
	public <T> T call(Function<Minecraft, T> task) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.isSameThread()) {
			return task.apply(minecraft);
		}
		return await(minecraft.submit(() -> task.apply(minecraft)), timeout);
	}

	/** Schedules {@code task} on the render thread without waiting (for long blocking actions). */
	public void post(java.util.function.Consumer<Minecraft> task) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> task.accept(minecraft));
	}

	public Duration timeout() {
		return timeout;
	}

	/** Waits for an asynchronous client result, translating failures to {@link GameStateException}. */
	public static <T> T await(CompletableFuture<T> future, Duration timeout) {
		try {
			return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			future.cancel(false);
			throw new GameStateException("The client did not answer within " + timeout.toMillis()
					+ " ms (window minimized or game loading?).", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GameStateException("Interrupted while waiting for the client thread.", e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new GameStateException("Client task failed: " + e.getCause(), e.getCause());
		}
	}
}
