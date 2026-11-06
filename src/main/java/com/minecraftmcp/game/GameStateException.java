package com.minecraftmcp.game;

/**
 * The game is not in a state that allows the requested operation (no world loaded, unknown
 * player, timeout on the game thread...). The message is shown to the AI as-is.
 */
public class GameStateException extends RuntimeException {

	public GameStateException(String message) {
		super(message);
	}

	public GameStateException(String message, Throwable cause) {
		super(message, cause);
	}
}
