package com.minecraftmcp.game.inspect;

import com.minecraftmcp.game.inspect.Snapshots.EntityDetails;
import com.minecraftmcp.game.inspect.Snapshots.EntitySummary;
import com.minecraftmcp.game.inspect.Snapshots.ItemDetails;
import com.minecraftmcp.game.inspect.Snapshots.PlayerInfo;
import com.minecraftmcp.game.inspect.Snapshots.ServerInfo;
import com.minecraftmcp.game.inspect.Snapshots.Vec;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Answers the inspection queries from what the client knows, when the game is joined to a remote
 * server and there is no local {@code MinecraftServer}. Implemented by the client side.
 */
public interface RemoteInspector {

	ServerInfo serverInfo();

	/** Only the local player is available remotely; other names are refused. */
	PlayerInfo player(Optional<String> name);

	ItemDetails itemDetails(Optional<String> playerName, OptionalInt slot);

	/**
	 * Entities loaded on the client around a center (the local player by default), nearest first.
	 * Only entities near the player are loaded, so far ones are not seen.
	 *
	 * @param type already normalized entity type id (e.g. {@code minecraft:zombie}), if filtering
	 */
	List<EntitySummary> entitiesAround(Optional<Vec> center, Optional<String> player, int radius,
									   Optional<String> type, int limit);

	/** Detailed view of a loaded entity by numeric id or UUID. */
	EntityDetails entity(String idOrUuid);
}
