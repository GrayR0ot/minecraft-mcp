package com.minecraftmcp.game.inspect;

import com.minecraftmcp.game.inspect.Snapshots.ItemDetails;
import com.minecraftmcp.game.inspect.Snapshots.PlayerInfo;
import com.minecraftmcp.game.inspect.Snapshots.ServerInfo;

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
}
