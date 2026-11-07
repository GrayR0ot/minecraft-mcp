package com.minecraftmcp.mcp.tools;

import com.minecraftmcp.game.combat.CombatLog;
import com.minecraftmcp.game.GameStateException;
import com.minecraftmcp.game.ServerAccess;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

/** Reads recent damage and death events, to debug fights and measure balance. */
public final class ReadCombatLogTool implements McpTool {

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"read_combat_log",
			"Read combat log",
			"Returns recent damage and death events of living entities: victim, attacker, direct source "
					+ "(projectile...), damage type, incoming damage vs health actually lost after armor/effects, health "
					+ "after. Use 'after_id' with the returned 'nextId' to follow new events. Ideal to find out what "
					+ "killed the player or to measure the damage of a weapon or spell.",
			Schema.object()
					.integer("after_id", "Only events newer than this cursor. Default 0.", false)
					.bool("players_only", "Only events where a player is victim or attacker. Default true.", false)
					.string("contains", "Case-insensitive filter on victim, attacker or damage type.", false)
					.integer("limit", "Max events (newest kept), 1-500. Default 50.", false)
					.build(),
			true, false);

	private final CombatLog combat;
	private final ServerAccess serverAccess;

	public ReadCombatLogTool(CombatLog combat, ServerAccess serverAccess) {
		this.combat = combat;
		this.serverAccess = serverAccess;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		if (serverAccess.current().isEmpty()) {
			throw new GameStateException("Damage events are recorded by the server this game runs: not available on a remote "
					+ "server (or when no world is loaded). Use run_command on the remote server, e.g. a damage log plugin, "
					+ "or read_logs.");
		}
		return ToolResult.json(combat.query(
				args.intOr("after_id", 0, 0, Integer.MAX_VALUE),
				args.boolOr("players_only", true),
				args.optString("contains"),
				args.intOr("limit", 50, 1, 500)));
	}
}
