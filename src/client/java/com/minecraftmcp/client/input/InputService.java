package com.minecraftmcp.client.input;

import com.minecraftmcp.client.ClientAccess;
import com.minecraftmcp.game.GameStateException;
import com.minecraftmcp.mixin.client.MinecraftInvoker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Simulates the local player's inputs (look, hotbar, mouse buttons, chat) on the render thread,
 * reusing vanilla code paths so that behaviour matches a real player.
 */
public final class InputService {

	private final ClientAccess client;

	public InputService(ClientAccess client) {
		this.client = client;
	}

	/** Supported actions. */
	public enum Action {
		/** Set camera yaw/pitch. */
		LOOK,
		/** Select a hotbar slot (0-8). */
		SELECT_SLOT,
		/** Right click once. */
		USE,
		/** Left click once. */
		ATTACK,
		/** Close the open screen (inventory, pause menu, chat...). */
		CLOSE_SCREEN,
		/** Send a chat message, or a command if it starts with '/'. */
		CHAT,
		/** Hold a key for some ticks (draw a bow, eat, walk, jump, sneak...). */
		HOLD,
		/** Respawn after death, like the death screen's "Respawn" button. */
		RESPAWN
	}

	/** Keys that can be held with {@link Action#HOLD}. */
	public enum HeldKey {
		FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT, USE, ATTACK;

		KeyMapping mapping(Options options) {
			return switch (this) {
				case FORWARD -> options.keyUp;
				case BACK -> options.keyDown;
				case LEFT -> options.keyLeft;
				case RIGHT -> options.keyRight;
				case JUMP -> options.keyJump;
				case SNEAK -> options.keyShift;
				case SPRINT -> options.keySprint;
				case USE -> options.keyUse;
				case ATTACK -> options.keyAttack;
			};
		}
	}

	/** Maximum duration of a {@link Action#HOLD}, in ticks (10 s). */
	public static final int MAX_HOLD_TICKS = 200;

	/** Remaining ticks of each held key. Render thread only. */
	private final Map<KeyMapping, Integer> heldKeys = new HashMap<>();

	/**
	 * Parameters of an action; unused fields are ignored.
	 */
	public record Request(Action action, float yaw, float pitch, int slot, String text, HeldKey key, int ticks, boolean waitForRelease) {
	}

	/** Releases held keys when their time is up. Call once from the client initializer. */
	public void registerEvents() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (heldKeys.isEmpty()) {
				return;
			}
			Iterator<Map.Entry<KeyMapping, Integer>> it = heldKeys.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<KeyMapping, Integer> entry = it.next();
				int remaining = entry.getValue() - 1;
				if (remaining <= 0) {
					entry.getKey().setDown(false);
					it.remove();
				} else {
					entry.setValue(remaining);
					entry.getKey().setDown(true); // re-assert: focus changes may reset key states
				}
			}
		});
	}

	/**
	 * Executes the action and returns a short human-readable outcome. For {@link Action#HOLD}, waits
	 * until the key is released so that the caller observes the finished action.
	 */
	public String perform(Request request) {
		String outcome = client.call(minecraft -> dispatch(minecraft, request));
		if (request.action() == Action.HOLD && request.waitForRelease()) {
			try {
				Thread.sleep(request.ticks() * 50L + 100L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		return outcome;
	}

	private String hold(Minecraft minecraft, HeldKey key, int ticks) {
		requireInGame(minecraft);
		if (ticks < 1 || ticks > MAX_HOLD_TICKS) {
			throw new GameStateException("'ticks' must be between 1 and " + MAX_HOLD_TICKS + ".");
		}
		KeyMapping mapping = key.mapping(minecraft.options);
		mapping.setDown(true);
		heldKeys.put(mapping, ticks);
		// A real press also produces a click: start the action the way vanilla does on key press.
		if (key == HeldKey.USE) {
			((MinecraftInvoker) minecraft).mcpMod$startUseItem();
		} else if (key == HeldKey.ATTACK) {
			((MinecraftInvoker) minecraft).mcpMod$startAttack();
		}
		return "Held " + key.name().toLowerCase(java.util.Locale.ROOT) + " for " + ticks + " ticks."
				+ (key == HeldKey.ATTACK ? " Note: continuous mining only works while the game window has focus." : "");
	}

	private String dispatch(Minecraft minecraft, Request request) {
		return switch (request.action()) {
			case HOLD -> hold(minecraft, request.key(), request.ticks());
			case RESPAWN -> {
				LocalPlayer player = requirePlayer(minecraft);
				if (!player.isDeadOrDying()) {
					yield "The player is alive; nothing to do.";
				}
				player.respawn();
				minecraft.gui.setScreen(null);
				yield "Respawn requested.";
			}
			case LOOK -> look(requirePlayer(minecraft), request.yaw(), request.pitch());
			case SELECT_SLOT -> selectSlot(requirePlayer(minecraft), request.slot());
			case USE -> {
				requireInGame(minecraft);
				((MinecraftInvoker) minecraft).mcpMod$startUseItem();
				yield "Used item / interacted.";
			}
			case ATTACK -> {
				requireInGame(minecraft);
				// Vanilla's return value only means "started breaking a block", so describe the target instead.
				String target = describeTarget(minecraft.hitResult);
				((MinecraftInvoker) minecraft).mcpMod$startAttack();
				yield "Attack performed on " + target + ". Check the target's state to confirm the effect.";
			}
			case CLOSE_SCREEN -> {
				if (minecraft.gui.screen() == null) {
					yield "No screen open.";
				}
				if (minecraft.player != null && minecraft.player.isDeadOrDying()) {
					throw new GameStateException("The player is dead: use action=respawn instead of close_screen.");
				}
				minecraft.gui.setScreen(null);
				yield "Screen closed.";
			}
			case CHAT -> chat(requirePlayer(minecraft), request.text());
		};
	}

	private static String look(LocalPlayer player, float yaw, float pitch) {
		float clampedPitch = Mth.clamp(pitch, -90.0F, 90.0F);
		player.setYRot(yaw);
		player.setXRot(clampedPitch);
		player.setYHeadRot(yaw);
		player.yRotO = yaw;
		player.xRotO = clampedPitch;
		return "Looking at yaw " + yaw + ", pitch " + clampedPitch + ".";
	}

	private static String selectSlot(LocalPlayer player, int slot) {
		if (slot < 0 || slot > 8) {
			throw new GameStateException("Hotbar slot must be between 0 and 8.");
		}
		player.getInventory().setSelectedSlot(slot);
		return "Selected hotbar slot " + slot + ".";
	}

	private static String chat(LocalPlayer player, String text) {
		if (text == null || text.isBlank()) {
			throw new GameStateException("Action 'chat' needs a non-empty 'text'.");
		}
		String message = text.strip();
		if (message.startsWith("/")) {
			player.connection.sendCommand(message.substring(1));
			return "Command sent as the player: " + message;
		}
		player.connection.sendChat(message);
		return "Chat message sent.";
	}

	private static String describeTarget(HitResult hit) {
		return switch (hit) {
			case EntityHitResult entityHit -> "entity #" + entityHit.getEntity().getId()
					+ " (" + entityHit.getEntity().getName().getString() + ")";
			case BlockHitResult blockHit when blockHit.getType() == HitResult.Type.BLOCK -> "block at " + blockHit.getBlockPos().toShortString();
			case null, default -> "air (swing only)";
		};
	}

	private static LocalPlayer requirePlayer(Minecraft minecraft) {
		if (minecraft.player == null) {
			throw new GameStateException("No local player: join a world first.");
		}
		return minecraft.player;
	}

	private static void requireInGame(Minecraft minecraft) {
		requirePlayer(minecraft);
		if (minecraft.gui.screen() != null) {
			throw new GameStateException("A screen is open (" + minecraft.gui.screen().getClass().getSimpleName()
					+ "); use action=close_screen first.");
		}
	}
}
