package com.minecraftmcp.client.command;

import com.minecraftmcp.client.ClientAccess;
import com.minecraftmcp.game.GameStateException;
import com.minecraftmcp.game.inspect.RemoteInspector;
import com.minecraftmcp.game.inspect.Snapshots.ItemDetails;
import com.minecraftmcp.game.inspect.Snapshots.PlayerInfo;
import com.minecraftmcp.game.inspect.Snapshots.ServerInfo;
import com.minecraftmcp.game.inspect.WorldInspectionService;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Inspection of a remote server from the client's point of view: only what the server synchronizes
 * to this client is available (the local player, loaded dimensions, the player list). MSPT and
 * other server internals are not.
 */
public final class RemoteInspectionService implements RemoteInspector {

	private final ClientAccess client;

	public RemoteInspectionService(ClientAccess client) {
		this.client = client;
	}

	@Override
	public ServerInfo serverInfo() {
		return client.call(minecraft -> {
			ClientPacketListener connection = requireConnection(minecraft);
			List<String> dimensions = connection.levels().stream().map(key -> key.identifier().toString()).sorted().toList();
			List<String> players = connection.getOnlinePlayers().stream().map(info -> info.getProfile().name()).sorted().toList();
			var level = minecraft.level;
			return new ServerInfo(
					SharedConstants.getCurrentVersion().name(),
					!minecraft.hasSingleplayerServer(),
					level.getGameTime(),
					null,
					level.tickRateManager().tickrate(),
					level.tickRateManager().isFrozen(),
					dimensions,
					players,
					level.getDefaultClockTime());
		});
	}

	@Override
	public PlayerInfo player(Optional<String> name) {
		return client.call(minecraft -> {
			LocalPlayer player = requireLocalPlayer(minecraft, name);
			return WorldInspectionService.describePlayer(player, minecraft.gameMode.getPlayerMode().getName());
		});
	}

	@Override
	public ItemDetails itemDetails(Optional<String> playerName, OptionalInt slot) {
		return client.call(minecraft -> {
			LocalPlayer player = requireLocalPlayer(minecraft, playerName);
			return WorldInspectionService.describeItem(player, slot, minecraft.level.registryAccess());
		});
	}

	private static ClientPacketListener requireConnection(Minecraft minecraft) {
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null || minecraft.level == null) {
			throw new GameStateException("Not connected to a world or server. Join one first (client_world).");
		}
		return connection;
	}

	private static LocalPlayer requireLocalPlayer(Minecraft minecraft, Optional<String> name) {
		requireConnection(minecraft);
		LocalPlayer player = minecraft.player;
		if (player == null) {
			throw new GameStateException("The local player is not in the world yet.");
		}
		if (name.isPresent() && !name.get().equalsIgnoreCase(player.getName().getString())) {
			throw new GameStateException("On a remote server only your own player ('" + player.getName().getString()
					+ "') can be inspected, not '" + name.get() + "'.");
		}
		return player;
	}
}
