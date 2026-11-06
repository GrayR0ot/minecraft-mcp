package com.minecraftmcp.client;

import com.minecraftmcp.client.chat.ChatCapture;
import com.minecraftmcp.client.chat.ChatCapture.ChatLine;
import com.minecraftmcp.game.inspect.Snapshots.Rotation;
import com.minecraftmcp.game.inspect.Snapshots.Vec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/** Snapshot of what the local client sees: screen, player, crosshair target and recent chat. */
public final class ClientStateService {

	private final ClientAccess client;
	private final ChatCapture chat;

	public ClientStateService(ClientAccess client, ChatCapture chat) {
		this.client = client;
		this.chat = chat;
	}

	public record ScreenInfo(String type, String title) {
	}

	public record LocalPlayerInfo(String name, Vec pos, Rotation rotation, float health, int selectedSlot, String heldItem) {
	}

	/**
	 * What the crosshair points at.
	 *
	 * @param type     {@code MISS}, {@code BLOCK} or {@code ENTITY}
	 * @param block    block id when type is BLOCK
	 * @param blockPos block position when type is BLOCK
	 * @param entityId numeric entity id when type is ENTITY
	 * @param entity   entity type when type is ENTITY
	 */
	public record CrosshairInfo(String type, String block, Vec blockPos, Integer entityId, String entity) {
	}

	public record ClientState(boolean inWorld, boolean singleplayer, boolean paused, int fps, int windowWidth,
							  int windowHeight, ScreenInfo screen, LocalPlayerInfo player, CrosshairInfo crosshair,
							  List<ChatLine> chat) {
	}

	/** Captures the state; {@code chatAfterId}/{@code chatLimit} select the chat lines returned. */
	public ClientState snapshot(long chatAfterId, int chatLimit) {
		ClientState state = client.call(minecraft -> {
			Screen screen = minecraft.gui.screen();
			LocalPlayer player = minecraft.player;
			return new ClientState(
					minecraft.level != null && player != null,
					minecraft.hasSingleplayerServer(),
					minecraft.isPaused(),
					minecraft.getFps(),
					minecraft.getWindow().getWidth(),
					minecraft.getWindow().getHeight(),
					screen == null ? null : new ScreenInfo(screen.getClass().getSimpleName(), screen.getTitle().getString()),
					player == null ? null : describe(player),
					crosshair(minecraft),
					List.of());
		});
		return new ClientState(state.inWorld(), state.singleplayer(), state.paused(), state.fps(), state.windowWidth(),
				state.windowHeight(), state.screen(), state.player(), state.crosshair(), chat.since(chatAfterId, chatLimit));
	}

	private static LocalPlayerInfo describe(LocalPlayer player) {
		return new LocalPlayerInfo(
				player.getName().getString(),
				Vec.of(player.getX(), player.getY(), player.getZ()),
				new Rotation(player.getYRot(), player.getXRot()),
				player.getHealth(),
				player.getInventory().getSelectedSlot(),
				BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
	}

	private static CrosshairInfo crosshair(Minecraft minecraft) {
		HitResult hit = minecraft.hitResult;
		if (hit == null || minecraft.level == null) {
			return null;
		}
		return switch (hit) {
			case BlockHitResult block when block.getType() == HitResult.Type.BLOCK -> {
				BlockPos pos = block.getBlockPos();
				String id = BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock()).toString();
				yield new CrosshairInfo("BLOCK", id, new Vec(pos.getX(), pos.getY(), pos.getZ()), null, null);
			}
			case EntityHitResult entityHit -> {
				Entity entity = entityHit.getEntity();
				yield new CrosshairInfo("ENTITY", null, null, entity.getId(),
						BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
			}
			default -> new CrosshairInfo("MISS", null, null, null, null);
		};
	}
}
