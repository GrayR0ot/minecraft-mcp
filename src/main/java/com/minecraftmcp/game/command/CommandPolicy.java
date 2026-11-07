package com.minecraftmcp.game.command;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Basic guard-rail for commands sent by the AI.
 *
 * <p>In restricted mode, commands whose root literal is in the deny list are refused, including
 * when nested behind {@code /execute ... run}. This protects the test environment against
 * accidental shutdowns or permission changes; it is not a security boundary against a hostile
 * client (datapack functions, for instance, are not inspected).
 */
public final class CommandPolicy {

	/** Hard limit mirroring vanilla's chat command limit. */
	public static final int MAX_COMMAND_LENGTH = 32_500;

	private final boolean restricted;
	private final Set<String> denied;

	public CommandPolicy(boolean restricted, Iterable<String> deniedRoots) {
		this.restricted = restricted;
		Set<String> names = new java.util.HashSet<>();
		deniedRoots.forEach(name -> names.add(normalizeRoot(name)));
		this.denied = Set.copyOf(names);
	}

	/**
	 * Normalizes a raw command (strips the leading slash and whitespace) and applies textual checks.
	 *
	 * @return the command ready to be parsed
	 * @throws CommandRejectedException if the command is empty, too long or denied
	 */
	public String prepare(String rawCommand) {
		String command = rawCommand == null ? "" : rawCommand.strip();
		// Strip exactly one slash, like the game does: "//set" is WorldEdit's "/set" command, not "set".
		if (command.startsWith("/")) {
			command = command.substring(1).stripLeading();
		}
		if (command.isEmpty()) {
			throw new CommandRejectedException("Empty command.");
		}
		if (command.length() > MAX_COMMAND_LENGTH) {
			throw new CommandRejectedException("Command too long (" + command.length() + " > " + MAX_COMMAND_LENGTH + " chars).");
		}
		if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
			throw new CommandRejectedException("Commands must be a single line; send several calls instead.");
		}
		String root = command.split("\\s+", 2)[0];
		checkRoot(root);
		return command;
	}

	/**
	 * Inspects a Brigadier parse tree (including redirects such as {@code execute run}) and refuses
	 * it if any executed command root is denied.
	 */
	public void check(ParseResults<?> parse) {
		for (String root : rootLiterals(parse)) {
			checkRoot(root);
		}
	}

	/** Root literal of each command level in the parse chain, e.g. {@code [execute, kill]}. */
	public static List<String> rootLiterals(ParseResults<?> parse) {
		List<String> roots = new ArrayList<>();
		CommandContextBuilder<?> context = parse.getContext();
		while (context != null) {
			List<? extends ParsedCommandNode<?>> nodes = context.getNodes();
			if (!nodes.isEmpty() && nodes.getFirst().getNode() instanceof LiteralCommandNode<?> literal) {
				roots.add(literal.getLiteral());
			}
			context = context.getChild();
		}
		return roots;
	}

	/**
	 * Textual safety net for {@code execute ... run <command>} when the command could not be fully
	 * parsed (e.g. arguments of a plugin command): refuses it if any word after "run" is a denied root.
	 */
	public void checkExecuteRuns(String command) {
		String[] words = command.strip().split("\\s+");
		if (words.length == 0 || !normalizeRoot(words[0]).equals("execute")) {
			return;
		}
		for (int i = 1; i < words.length - 1; i++) {
			if (words[i].equals("run")) {
				checkRoot(words[i + 1]);
			}
		}
	}

	public boolean isRestricted() {
		return restricted;
	}

	/** Denied root names, sorted, for display. */
	public String describeDenied() {
		return denied.stream().sorted().collect(Collectors.joining(", "));
	}

	private void checkRoot(String root) {
		if (restricted && denied.contains(normalizeRoot(root))) {
			throw new CommandRejectedException("Command '/" + root + "' is blocked by the MCP command policy (restricted mode). "
					+ "Denied roots: " + describeDenied() + ". Change 'commands.mode' in config/minecraft_mcp.json if this is intended.");
		}
	}

	private static String normalizeRoot(String root) {
		String name = root.toLowerCase(Locale.ROOT);
		int namespace = name.indexOf(':');
		return namespace >= 0 ? name.substring(namespace + 1) : name;
	}
}
