package com.minecraftmcp.game.command;

import com.mojang.brigadier.ParseResults;
import com.minecraftmcp.game.ServerAccess;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Executes server commands on behalf of the AI and captures everything they print.
 */
public final class CommandService {

	private static final Logger LOGGER = LoggerFactory.getLogger("MCP");
	private static final int MAX_OUTPUT_LINES = 200;

	private final ServerAccess serverAccess;
	private final CommandPolicy policy;
	private volatile RemoteCommandExecutor remote;

	public CommandService(ServerAccess serverAccess, CommandPolicy policy) {
		this.serverAccess = serverAccess;
		this.policy = policy;
	}

	/**
	 * Outcome of a command.
	 *
	 * @param command  the normalized command that ran
	 * @param executor "server" or the player name the command ran as
	 * @param success  vanilla success flag, or {@code null} if the command never reported a result
	 *                 (typically a parse error)
	 * @param result   vanilla result value (e.g. number of affected entities)
	 * @param output   chat lines the command sent to its source
	 * @param note     set when the result is incomplete (remote server: success and result are not reported)
	 */
	public record CommandOutcome(String command, String executor, Boolean success, int result, List<String> output,
								 String note) {
	}

	/**
	 * Runs {@code rawCommand} with operator permissions, either as the server console or as an
	 * online player (to make {@code @s} and relative coordinates refer to that player).
	 */
	public CommandOutcome execute(String rawCommand, Optional<String> asPlayer) {
		RemoteCommandExecutor remoteExecutor = remote;
		if (serverAccess.current().isEmpty() && remoteExecutor != null) {
			if (asPlayer.isPresent()) {
				throw new CommandRejectedException("as_player is not available on a remote server: the command runs as your own player.");
			}
			return remoteExecutor.execute(rawCommand);
		}
		String command = policy.prepare(rawCommand);
		return serverAccess.call(server -> {
			OutputCollector output = new OutputCollector();
			ResultHolder holder = new ResultHolder();

			CommandSourceStack base;
			String executor;
			if (asPlayer.isPresent()) {
				ServerPlayer player = ServerAccess.resolvePlayer(server, asPlayer);
				base = player.createCommandSourceStack().withPermission(server.createCommandSourceStack().permissions());
				executor = player.getName().getString();
			} else {
				base = server.createCommandSourceStack();
				executor = "server";
			}
			CommandSourceStack source = base.withSource(output).withCallback(holder);

			ParseResults<CommandSourceStack> parse = server.getCommands().getDispatcher().parse(command, source);
			policy.check(parse);

			LOGGER.info("[MCP] /{} (as {})", command, executor);
			server.getCommands().performCommand(parse, command);
			return new CommandOutcome(command, executor, holder.success, holder.value, output.lines(), null);
		});
	}

	/** Installs the executor used when joined to a remote server (client side). */
	public void setRemoteExecutor(RemoteCommandExecutor executor) {
		this.remote = executor;
	}

	public CommandPolicy policy() {
		return policy;
	}

	/** Collects every message a command sends to its source. Used on the server thread only. */
	private static final class OutputCollector implements CommandSource {
		private final List<String> lines = new ArrayList<>();
		private int dropped;

		@Override
		public void sendSystemMessage(Component message) {
			if (lines.size() < MAX_OUTPUT_LINES) {
				lines.add(message.getString());
			} else {
				dropped++;
			}
		}

		@Override
		public boolean acceptsSuccess() {
			return true;
		}

		@Override
		public boolean acceptsFailure() {
			return true;
		}

		@Override
		public boolean shouldInformAdmins() {
			return false;
		}

		List<String> lines() {
			if (dropped == 0) {
				return Collections.unmodifiableList(lines);
			}
			List<String> copy = new ArrayList<>(lines);
			copy.add("... " + dropped + " more line(s) truncated");
			return copy;
		}
	}

	/** Receives the final result of the command. */
	private static final class ResultHolder implements CommandResultCallback {
		private Boolean success;
		private int value;

		@Override
		public void onResult(boolean success, int value) {
			this.success = success;
			this.value = value;
		}
	}
}
