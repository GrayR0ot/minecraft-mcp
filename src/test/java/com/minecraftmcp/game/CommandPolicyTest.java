package com.minecraftmcp.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.minecraftmcp.game.command.CommandPolicy;
import com.minecraftmcp.game.command.CommandRejectedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandPolicyTest {

	private final CommandPolicy restricted = new CommandPolicy(true, List.of("stop", "op"));

	@Test
	void stripsLeadingSlashes() {
		assertEquals("time set day", restricted.prepare("  //time set day "));
	}

	@Test
	void rejectsDeniedRootTextually() {
		assertThrows(CommandRejectedException.class, () -> restricted.prepare("/stop"));
		assertThrows(CommandRejectedException.class, () -> restricted.prepare("minecraft:op Steve"));
	}

	@Test
	void rejectsEmptyAndMultiline() {
		assertThrows(CommandRejectedException.class, () -> restricted.prepare("  / "));
		assertThrows(CommandRejectedException.class, () -> restricted.prepare("say a\nstop"));
	}

	@Test
	void unrestrictedAllowsEverything() {
		CommandPolicy open = new CommandPolicy(false, List.of("stop"));
		assertDoesNotThrow(() -> open.prepare("stop"));
	}

	@Test
	void detectsDeniedCommandBehindExecuteRedirect() {
		CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
		dispatcher.register(LiteralArgumentBuilder.literal("stop").executes(c -> 1));
		dispatcher.register(LiteralArgumentBuilder.literal("say").executes(c -> 1));
		LiteralCommandNode<Object> execute = dispatcher.register(LiteralArgumentBuilder.literal("execute"));
		dispatcher.register(LiteralArgumentBuilder.literal("execute")
				.then(LiteralArgumentBuilder.literal("run").redirect(dispatcher.getRoot())));

		var parse = dispatcher.parse("execute run stop", new Object());
		assertEquals(List.of("execute", "stop"), CommandPolicy.rootLiterals(parse));
		assertThrows(CommandRejectedException.class, () -> restricted.check(parse));
		assertDoesNotThrow(() -> restricted.check(dispatcher.parse("execute run say", new Object())));
		assertEquals("execute", execute.getLiteral());
	}

	@Test
	void textualExecuteGuardCatchesDeniedRootAfterRun() {
		assertThrows(CommandRejectedException.class, () -> restricted.checkExecuteRuns("execute as @a at @s run stop"));
		assertThrows(CommandRejectedException.class, () -> restricted.checkExecuteRuns("execute run minecraft:op Steve"));
		assertDoesNotThrow(() -> restricted.checkExecuteRuns("execute as @a run say hi"));
		// Only /execute is inspected: other commands may legitimately contain the word "run".
		assertDoesNotThrow(() -> restricted.checkExecuteRuns("say run stop"));
	}
}
