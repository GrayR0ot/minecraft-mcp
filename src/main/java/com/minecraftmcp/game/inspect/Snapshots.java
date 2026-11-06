package com.minecraftmcp.game.inspect;

import java.util.List;

/**
 * Immutable, thread-safe views of game objects, captured on the server thread and serialized to
 * JSON by the MCP layer. Field names are part of the tool output contract: keep them stable.
 */
public final class Snapshots {

	private Snapshots() {
	}

	/** A position rounded to 2 decimals for readability. */
	public record Vec(double x, double y, double z) {
		public static Vec of(double x, double y, double z) {
			return new Vec(round(x), round(y), round(z));
		}
	}

	public record Rotation(float yaw, float pitch) {
	}

	public record ItemInfo(int slot, String item, int count, String name) {
	}

	public record EffectInfo(String effect, int amplifier, int durationTicks) {
	}

	public record AttributeInfo(String attribute, double base, double value, int modifiers) {
	}

	/** Short description of any entity, used in lists. */
	public record EntitySummary(int id, String uuid, String type, String name, String dimension, Vec pos,
								Float health, Float maxHealth, Double distance, List<String> tags) {
	}

	/** Detailed description of one entity. */
	public record EntityDetails(EntitySummary summary, Rotation rotation, Vec motion, boolean alive,
								List<AttributeInfo> attributes, List<EffectInfo> effects,
								List<ItemInfo> equipment) {
	}

	/** Full description of a player. */
	public record PlayerInfo(String name, String uuid, String dimension, Vec pos, Rotation rotation,
							 String gameMode, float health, float maxHealth, int food, float saturation,
							 int xpLevel, int selectedSlot, ItemInfo heldItem, List<ItemInfo> inventory,
							 List<EffectInfo> effects) {
	}

	/** Global state of the server. */
	public record ServerInfo(String minecraftVersion, boolean dedicated, long tickCount, double mspt,
							 float targetTps, boolean tickFrozen, List<String> dimensions,
							 List<String> players, long dayTime) {
	}

	static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
