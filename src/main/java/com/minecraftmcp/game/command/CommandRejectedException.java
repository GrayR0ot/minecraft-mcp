package com.minecraftmcp.game.command;

import com.minecraftmcp.game.GameStateException;

/** A command was refused by the {@link CommandPolicy} before execution. */
public class CommandRejectedException extends GameStateException {

	public CommandRejectedException(String message) {
		super(message);
	}
}
