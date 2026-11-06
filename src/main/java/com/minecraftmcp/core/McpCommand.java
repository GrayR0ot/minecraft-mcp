package com.minecraftmcp.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * In-game {@code /mcp} command: lets a human check the MCP endpoint.
 * All texts are translatable ({@code assets/minecraft_mcp/lang}).
 */
final class McpCommand {

	private McpCommand() {
	}

	static void register(McpRuntime runtime) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher, runtime));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher, McpRuntime runtime) {
		dispatcher.register(Commands.literal("mcp")
				.then(Commands.literal("status")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> status(context, runtime))));
	}

	private static int status(CommandContext<CommandSourceStack> context, McpRuntime runtime) {
		Component message = runtime.endpointUrl()
				.map(url -> (Component) Component.translatable("commands.minecraft_mcp.status.running", url, runtime.tools().size()))
				.orElseGet(() -> Component.translatable("commands.minecraft_mcp.status.stopped"));
		context.getSource().sendSuccess(() -> message, false);
		return runtime.endpointUrl().isPresent() ? 1 : 0;
	}
}
