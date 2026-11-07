package com.minecraftmcp.client.tools;

import com.minecraftmcp.client.world.WorldSessionService;
import com.minecraftmcp.client.world.WorldSessionService.Preset;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolException;
import com.minecraftmcp.mcp.tool.ToolResult;
import net.minecraft.world.level.GameType;

import java.util.OptionalLong;

/** Lists, creates, opens and leaves singleplayer worlds, and joins multiplayer servers. */
public final class ClientWorldTool implements McpTool {

	/** Actions of this tool. */
	public enum WorldAction { LIST, CREATE, OPEN, JOIN, LEAVE }

	/** Game modes accepted on creation. */
	public enum Mode { CREATIVE, SURVIVAL, ADVENTURE, SPECTATOR }

	private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
			"client_world",
			"Client world",
			"Manages worlds and connections. 'list' shows saved worlds (id, name, last played). 'create' makes and "
					+ "joins a new world (cheats on; preset 'flat' is fastest for tests). 'open' joins a saved world "
					+ "by id. 'join' connects to a multiplayer server by 'address' (the server-side tools then do "
						+ "not apply: use run_command, list_commands and the client tools). 'leave' saves and returns to the title screen. create/open/join/leave wait until done "
					+ "(up to " + WorldSessionService.WORLD_TIMEOUT.toSeconds() + " s) unless wait=false.",
			Schema.object()
					.enumeration("action", WorldAction.class, "What to do.", true)
					.string("name", "For 'create': world name (letters, digits, space, _ or -). For 'open': world id.", false)
					.enumeration("preset", Preset.class, "For 'create': terrain. Default flat.", false)
					.enumeration("game_mode", Mode.class, "For 'create': game mode. Default creative.", false)
					.string("seed", "For 'create': numeric seed. Random if absent.", false)
					.string("address", "For 'join': server address, host or host:port.", false)
					.enumeration("resource_pack", WorldSessionService.ResourcePack.class,
							"For 'join': how to answer the server's resource pack offer. Default accept. 'prompt' shows vanilla's "
							+ "confirmation screen, which you cannot answer.", false)
					.bool("wait", "Wait for the operation to finish. Default true.", false)
					.build(),
			false, false);

	private final WorldSessionService worlds;

	public ClientWorldTool(WorldSessionService worlds) {
		this.worlds = worlds;
	}

	@Override
	public ToolDescriptor descriptor() {
		return DESCRIPTOR;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		WorldAction action = args.optEnum("action", WorldAction.class)
				.orElseThrow(() -> new ToolException("Missing required argument 'action'."));
		boolean wait = args.boolOr("wait", true);
		return switch (action) {
			case LIST -> ToolResult.json(worlds.list());
			case CREATE -> ToolResult.json(worlds.create(
					args.requireString("name"),
					args.optEnum("preset", Preset.class).orElse(Preset.FLAT),
					toGameType(args.optEnum("game_mode", Mode.class).orElse(Mode.CREATIVE)),
					parseSeed(args),
					wait));
			case OPEN -> ToolResult.json(worlds.open(args.requireString("name"), wait));
			case JOIN -> ToolResult.json(worlds.join(args.requireString("address"), wait,
					args.optEnum("resource_pack", WorldSessionService.ResourcePack.class).orElse(WorldSessionService.ResourcePack.ACCEPT)));
			case LEAVE -> ToolResult.json(worlds.leave(wait));
		};
	}

	private static GameType toGameType(Mode mode) {
		return switch (mode) {
			case CREATIVE -> GameType.CREATIVE;
			case SURVIVAL -> GameType.SURVIVAL;
			case ADVENTURE -> GameType.ADVENTURE;
			case SPECTATOR -> GameType.SPECTATOR;
		};
	}

	private static OptionalLong parseSeed(ToolArgs args) {
		return args.optString("seed").map(seed -> {
			try {
				return OptionalLong.of(Long.parseLong(seed));
			} catch (NumberFormatException e) {
				throw new ToolException("'seed' must be a whole number.");
			}
		}).orElse(OptionalLong.empty());
	}
}
