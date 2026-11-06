package com.minecraftmcp.game.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Records damage and deaths of living entities, so the AI can answer "what hit me, for how
 * much?" and measure combat balance (DPS, time-to-kill, damage reduction).
 *
 * <p>Events are produced on the server thread and read from MCP worker threads.
 */
public final class CombatLog {

	private final int capacity;
	private final ArrayDeque<CombatEvent> events;
	private long nextId = 1;

	/**
	 * One damage or death event.
	 *
	 * @param id          monotonically increasing cursor
	 * @param gameTime    level game time (ticks) when it happened
	 * @param kind        {@code damage} or {@code death}
	 * @param victim      victim description, e.g. {@code minecraft:player McpTester #12}
	 * @param attacker    entity responsible (owner of a projectile), if any
	 * @param direct      entity that directly dealt the damage (projectile, part...), if any
	 * @param damageType  damage type id, e.g. {@code minecraft:mob_attack}
	 * @param incomingDamage damage of the hit before armor, enchantments and effects (damage events)
	 * @param healthLost     health + absorption actually lost to this hit, i.e. after every
	 *                       reduction (damage events); compare with incomingDamage to measure mitigation
	 * @param blocked        {@code true} if a shield blocked it
	 * @param healthAfter    victim health after the event
	 * @param absorptionAfter victim absorption hearts after the event
	 * @param involvesPlayer {@code true} if a player is the victim or the attacker
	 */
	public record CombatEvent(long id, long gameTime, String kind, String victim, String attacker, String direct,
							  String damageType, Float incomingDamage, Float healthLost, Boolean blocked,
							  float healthAfter, float absorptionAfter, boolean involvesPlayer) {
	}

	/** Health + absorption of entities about to take damage. Server thread only. */
	private final Map<LivingEntity, Float> poolBeforeDamage = new WeakHashMap<>();

	/**
	 * @param entries matching events, oldest first
	 * @param nextId  cursor to pass as {@code afterId} next time
	 */
	public record CombatPage(List<CombatEvent> entries, long nextId) {
	}

	public CombatLog(int capacity) {
		this.capacity = capacity;
		this.events = new ArrayDeque<>(capacity);
	}

	/** Subscribes to Fabric's living-entity events. Call once from the mod initializer. */
	public void registerEvents() {
		// Fabric's damage values are pre-mitigation: snapshot the health pool to measure the real loss.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			poolBeforeDamage.put(entity, entity.getHealth() + entity.getAbsorptionAmount());
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			Float before = poolBeforeDamage.remove(entity);
			Float lost = before == null ? null : Math.max(0f, before - entity.getHealth() - entity.getAbsorptionAmount());
			record(entity, source, "damage", damageTaken, lost, blocked);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
				record(entity, source, "death", null, null, null));
	}

	/**
	 * Queries the log.
	 *
	 * @param afterId     only events with {@code id > afterId}
	 * @param playersOnly keep only events where a player is victim or attacker
	 * @param contains    case-insensitive filter on victim/attacker/type
	 * @param limit       max events (newest kept)
	 */
	public CombatPage query(long afterId, boolean playersOnly, Optional<String> contains, int limit) {
		Optional<String> needle = contains.map(s -> s.toLowerCase(Locale.ROOT));
		List<CombatEvent> matches = new ArrayList<>();
		long next;
		synchronized (events) {
			next = nextId - 1;
			for (CombatEvent event : events) {
				if (event.id() <= afterId) continue;
				if (playersOnly && !event.involvesPlayer()) continue;
				if (needle.isPresent() && !(event.victim() + " " + event.attacker() + " " + event.direct() + " " + event.damageType())
						.toLowerCase(Locale.ROOT).contains(needle.get())) continue;
				matches.add(event);
			}
		}
		if (matches.size() > limit) {
			matches = new ArrayList<>(matches.subList(matches.size() - limit, matches.size()));
		}
		return new CombatPage(List.copyOf(matches), next);
	}

	private void record(LivingEntity victim, DamageSource source, String kind, Float raw, Float dealt, Boolean blocked) {
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		boolean involvesPlayer = victim instanceof Player || attacker instanceof Player;
		String damageType = source.typeHolder().getRegisteredName();
		CombatEvent event;
		synchronized (events) {
			event = new CombatEvent(nextId++, victim.level().getGameTime(), kind, describe(victim), describe(attacker),
					direct == attacker ? null : describe(direct), damageType, round(raw), round(dealt), blocked,
					round(victim.getHealth()), round(victim.getAbsorptionAmount()), involvesPlayer);
			if (events.size() >= capacity) {
				events.removeFirst();
			}
			events.addLast(event);
		}
	}

	private static String describe(Entity entity) {
		if (entity == null) {
			return null;
		}
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + " " + entity.getName().getString() + " #" + entity.getId();
	}

	private static Float round(Float value) {
		return value == null ? null : Math.round(value * 100f) / 100f;
	}
}
