package com.minecraftmcp.client.tools;

import com.minecraftmcp.client.command.CommandCatalogService;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Lists the commands the connected server allows this player to run, with their syntax. */
public final class ListCommandsTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"list_commands",
			"List commands",
			"Lists the commands the connected server (local or remote) lets this player run, with their "
					+ "syntax, e.g. '/give <targets> <item> [<count>]'. The list comes from the command tree the server "
					+ "sent at join time, so it includes plugin and WorldEdit commands and is limited by the player's "
					+ "permissions. Use 'filter' (substring of the command name; 'worldedit' matches WorldEdit's //commands) "
					+ "to look something up. Reports whether WorldEdit is present. Without a filter, prefer usages=false "
					+ "to get names only.",
			Schema.object()
					.string("filter", "Case-insensitive substring of the command name, or 'worldedit'.", false)
					.bool("usages", "Include syntaxes (default true). Use false for names only.", false)
					.integer("limit", "Maximum number of commands, 1-200. Default 50.", false)
					.build(),
			true, false);

	private final CommandCatalogService catalog;

	public ListCommandsTool(CommandCatalogService catalog) {
		this.catalog = catalog;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		return ToolResult.json(catalog.list(args.optString("filter"), args.boolOr("usages", true),
				args.intOr("limit", 50, 1, 200)));
	}
}
