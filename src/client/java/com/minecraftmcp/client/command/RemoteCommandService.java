package com.minecraftmcp.client.command;

import com.minecraftmcp.client.ClientAccess;
import com.minecraftmcp.client.chat.ChatCapture;
import com.minecraftmcp.client.chat.ChatCapture.ChatLine;
import com.minecraftmcp.game.GameStateException;
import com.minecraftmcp.game.command.CommandPolicy;
import com.minecraftmcp.game.command.CommandService.CommandOutcome;
import com.minecraftmcp.game.command.RemoteCommandExecutor;
import com.mojang.brigadier.ParseResults;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runs commands on a remote server by sending them through the chat as the local player, and
 * collects the server's answer from the received messages.
 *
 * <p>The command policy is applied before anything is sent, using the command tree the server sent
 * to the client (so {@code /execute ... run stop} is caught too). {@link #guard} is shared with the
 * {@code client_input} chat action so that it cannot be used to bypass the policy.
 */
public final class RemoteCommandService implements RemoteCommandExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger("MCP");
	private static final int MAX_OUTPUT_LINES = 200;
	/** Wait this long for a first answer; many commands (e.g. some WorldEdit ones) print nothing. */
	private static final long FIRST_ANSWER_WAIT_MS = 2_500;
	/** The answer is considered complete after this much silence. */
	private static final long QUIET_MS = 600;
	private static final long MAX_WAIT_MS = 15_000;
	private static final long POLL_MS = 50;

	/** Vanilla translation keys meaning the command did not run. */
	private static final Set<String> FAILURE_KEYS = Set.of(
			"command.unknown.command", "command.unknown.argument", "command.expected.separator",
			"command.exception", "command.failed", "command.context.here", "commands.help.failed",
			"permissions.requires.player", "permissions.requires.entity");

	private final ClientAccess client;
	private final ChatCapture chat;
	private final CommandPolicy policy;

	public RemoteCommandService(ClientAccess client, ChatCapture chat, CommandPolicy policy) {
		this.client = client;
		this.chat = chat;
		this.policy = policy;
	}

	/**
	 * Applies the command policy to a command about to be sent as the player.
	 *
	 * @return the normalized command (no leading slash)
	 * @throws com.minecraftmcp.game.command.CommandRejectedException if the policy refuses it
	 */
	public String guard(String rawCommand) {
		String command = policy.prepare(rawCommand);
		policy.checkExecuteRuns(command);
		return client.call(minecraft -> {
			ClientPacketListener connection = minecraft.getConnection();
			if (connection != null) {
				ParseResults<ClientSuggestionProvider> parse = connection.getCommands()
						.parse(command, connection.getSuggestionsProvider());
				policy.check(parse);
			}
			return command;
		});
	}

	@Override
	public CommandOutcome execute(String rawCommand) {
		String command = guard(rawCommand);
		long after = chat.lastId();
		String player = client.call(minecraft -> {
			if (minecraft.player == null || minecraft.getConnection() == null) {
				throw new GameStateException("Not connected to a world or server. Join one first (client_world, or join a server).");
			}
			LOGGER.info("[MCP] /{} (remote, as {})", command, minecraft.player.getName().getString());
			minecraft.player.connection.sendCommand(command);
			return minecraft.player.getName().getString();
		});

		List<ChatLine> answer = awaitAnswer(after);
		List<String> output = new ArrayList<>();
		boolean failed = false;
		for (ChatLine line : answer) {
			if (output.size() < MAX_OUTPUT_LINES) {
				output.add(line.text());
			}
			failed |= Boolean.TRUE.equals(line.error()) || (line.key() != null && FAILURE_KEYS.contains(line.key()));
		}
		String note = "Sent through the chat to a remote server: 'success' and 'result' are not reported by the server. "
				+ (answer.isEmpty()
				? "The server printed nothing within " + FIRST_ANSWER_WAIT_MS + " ms (many commands are silent; check the effect with another tool)."
				: "Read 'output' for the server's answer.");
		return new CommandOutcome(command, player + " (remote)", failed ? Boolean.FALSE : null, 0, output, note);
	}

	/** Collects non-chat messages received after {@code after} until the server goes quiet. */
	private List<ChatLine> awaitAnswer(long after) {
		long start = System.currentTimeMillis();
		long lastChange = start;
		int seen = 0;
		List<ChatLine> lines = List.of();
		while (true) {
			try {
				Thread.sleep(POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new GameStateException("Interrupted while waiting for the server's answer.", e);
			}
			long now = System.currentTimeMillis();
			lines = chat.since(after, MAX_OUTPUT_LINES).stream().filter(line -> !line.kind().equals("chat")).toList();
			if (lines.size() != seen) {
				seen = lines.size();
				lastChange = now;
			}
			boolean quiet = seen > 0 && now - lastChange >= QUIET_MS;
			boolean noAnswer = seen == 0 && now - start >= FIRST_ANSWER_WAIT_MS;
			if (quiet || noAnswer || now - start >= MAX_WAIT_MS) {
				return lines;
			}
		}
	}
}
