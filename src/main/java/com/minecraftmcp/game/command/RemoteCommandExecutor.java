package com.minecraftmcp.game.command;

import com.minecraftmcp.game.command.CommandService.CommandOutcome;

/**
 * Runs a command through the connected client (chat), for the case where the game is joined to a
 * remote server and there is no local {@code MinecraftServer}. Implemented by the client side.
 */
public interface RemoteCommandExecutor {

	/**
	 * Sends {@code rawCommand} as the local player, applies the command policy and collects the
	 * server's answer.
	 *
	 * @throws CommandRejectedException if the policy refuses the command
	 */
	CommandOutcome execute(String rawCommand);
}
