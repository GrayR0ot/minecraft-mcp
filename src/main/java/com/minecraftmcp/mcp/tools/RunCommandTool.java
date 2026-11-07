package com.minecraftmcp.mcp.tools;

import com.minecraftmcp.game.command.CommandService;
import com.minecraftmcp.game.command.CommandService.CommandOutcome;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Runs a server command and returns its success flag, result value and chat output. */
public final class RunCommandTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"run_command",
			"Run command",
			"Executes a Minecraft command with operator permissions and returns success, result value and "
					+ "every line it printed. By default runs as the server console; set 'as_player' to run as a "
					+ "player so that @s, ~ ~ ~ and ^ ^ ^ refer to them. Examples: 'give @p diamond_sword', "
					+ "'summon zombie ~ ~ ~5', 'data get entity @p', 'tick freeze'. Some commands (stop, op, "
					+ "ban...) are blocked by the safety policy. When joined to a remote server, the command is sent through the chat as "
					+ "your own player (with your permissions): 'success' may then be null, read 'output' and 'note'. "
					+ "WorldEdit commands are accepted with their double slash ('//pos1 0,64,0', '//set stone'); the selection "
					+ "belongs to the player, so set pos1 and pos2 first. Use list_commands (filter=worldedit) for the syntax.",
			Schema.object()
					.string("command", "Command, with or without the leading slash. Single line.", true)
					.string("as_player", "Optional name of an online player to execute as.", false)
					.build(),
			false, true);

	private final CommandService commands;

	public RunCommandTool(CommandService commands) {
		this.commands = commands;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		CommandOutcome outcome = commands.execute(args.requireString("command"), args.optString("as_player"));
		ToolResult result = ToolResult.json(outcome);
		// A command that never reported success (parse error, unknown command) is a failure too.
		boolean failed = Boolean.FALSE.equals(outcome.success()) || (outcome.success() == null && outcome.note() == null);
		return failed ? new ToolResult(result.content(), true) : result;
	}
}
