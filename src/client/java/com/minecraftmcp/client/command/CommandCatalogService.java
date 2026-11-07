package com.minecraftmcp.client.command;

import com.minecraftmcp.client.ClientAccess;
import com.minecraftmcp.game.GameStateException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lists the commands the connected server told this client about. The server sends its command
 * tree at join time, already filtered by the player's permissions, so this works on remote servers
 * and shows exactly what the player may run (plugin and WorldEdit commands included).
 */
public final class CommandCatalogService {

	/** Maximum usage lines returned per command, to keep answers small. */
	private static final int MAX_USAGES_PER_COMMAND = 15;
	/** Marker filter that also matches WorldEdit's {@code //command} roots (named "/set", "/pos1"...). */
	private static final String WORLDEDIT_FILTER = "worldedit";

	private final ClientAccess client;

	public CommandCatalogService(ClientAccess client) {
		this.client = client;
	}

	/**
	 * One root command.
	 *
	 * @param name   name as typed after the first slash (WorldEdit's {@code //set} appears as {@code /set})
	 * @param usages syntaxes, e.g. {@code /give <targets> <item> [<count>]}; absent in names-only mode
	 */
	public record CommandInfo(String name, List<String> usages) {
	}

	/**
	 * @param total          number of root commands available to the player
	 * @param matched        number matching the filter
	 * @param worldEdit      whether WorldEdit's commands are present in the tree
	 * @param operatorLevel  whether the client considers the player a game master (can run /gamemode etc.)
	 * @param commands       the matching commands, alphabetical, capped by the requested limit
	 */
	public record Catalog(int total, int matched, boolean worldEdit, boolean gameMaster, List<CommandInfo> commands) {
	}

	/**
	 * @param filter     case-insensitive substring of the root command name, or {@code worldedit}
	 * @param withUsages include syntaxes (otherwise names only)
	 * @param limit      maximum number of commands returned
	 */
	public Catalog list(Optional<String> filter, boolean withUsages, int limit) {
		String needle = filter.map(f -> f.strip().toLowerCase(Locale.ROOT)).orElse("");
		return client.call(minecraft -> {
			ClientPacketListener connection = minecraft.getConnection();
			if (connection == null || minecraft.player == null) {
				throw new GameStateException("Not connected to a world or server. Join one first (client_world, or join a server).");
			}
			CommandDispatcher<ClientSuggestionProvider> dispatcher = connection.getCommands();
			ClientSuggestionProvider source = connection.getSuggestionsProvider();

			List<CommandNode<ClientSuggestionProvider>> roots = new ArrayList<>(dispatcher.getRoot().getChildren());
			roots.sort(Comparator.comparing(CommandNode::getName));

			boolean worldEdit = roots.stream().anyMatch(node -> isWorldEditRoot(node.getName()));
			List<CommandNode<ClientSuggestionProvider>> matching = roots.stream()
					.filter(node -> matches(node.getName(), needle))
					.toList();

			List<CommandInfo> commands = new ArrayList<>();
			for (CommandNode<ClientSuggestionProvider> node : matching.stream().limit(limit).toList()) {
				commands.add(new CommandInfo(node.getName(), withUsages ? usages(dispatcher, node, source) : null));
			}
			return new Catalog(roots.size(), matching.size(), worldEdit,
					minecraft.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER),
					commands);
		});
	}

	private static boolean isWorldEditRoot(String name) {
		return name.startsWith("/") || name.equals("worldedit") || name.equals("we");
	}

	private static boolean matches(String name, String needle) {
		if (needle.isEmpty()) {
			return true;
		}
		if (needle.equals(WORLDEDIT_FILTER) && isWorldEditRoot(name)) {
			return true;
		}
		return name.toLowerCase(Locale.ROOT).contains(needle);
	}

	private static List<String> usages(CommandDispatcher<ClientSuggestionProvider> dispatcher,
									   CommandNode<ClientSuggestionProvider> node, ClientSuggestionProvider source) {
		String base = "/" + node.getName();
		List<String> lines = new ArrayList<>();
		if (node.getCommand() != null) {
			lines.add(base);
		}
		for (String usage : dispatcher.getAllUsage(node, source, true)) {
			if (lines.size() >= MAX_USAGES_PER_COMMAND) {
				lines.add("... (more variants; narrow the filter or ask the server)");
				break;
			}
			lines.add(usage.isEmpty() ? base : base + " " + usage);
		}
		return lines;
	}
}
