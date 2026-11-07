package com.minecraftmcp.game.inspect;

import com.minecraftmcp.game.GameStateException;
import com.minecraftmcp.game.ServerAccess;
import com.minecraftmcp.game.inspect.Snapshots.AttributeInfo;
import com.minecraftmcp.game.inspect.Snapshots.EffectInfo;
import com.minecraftmcp.game.inspect.Snapshots.EntityDetails;
import com.minecraftmcp.game.inspect.Snapshots.EntitySummary;
import com.minecraftmcp.game.inspect.Snapshots.ItemDetails;
import com.minecraftmcp.game.inspect.Snapshots.ItemInfo;
import com.minecraftmcp.game.inspect.Snapshots.PlayerInfo;
import com.minecraftmcp.game.inspect.Snapshots.Rotation;
import com.minecraftmcp.game.inspect.Snapshots.ServerInfo;
import com.minecraftmcp.game.inspect.Snapshots.Vec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only queries on the world. Every public method hops to the server thread through
 * {@link ServerAccess} and returns immutable {@link Snapshots}.
 */
public final class WorldInspectionService {

	/** Hard cap on the radius of entity searches, to keep queries cheap. */
	public static final int MAX_RADIUS = 256;

	private final ServerAccess serverAccess;
	private volatile RemoteInspector remote;

	public WorldInspectionService(ServerAccess serverAccess) {
		this.serverAccess = serverAccess;
	}

	/** Installs the inspector used when joined to a remote server (client side). */
	public void setRemoteInspector(RemoteInspector inspector) {
		this.remote = inspector;
	}

	private RemoteInspector remoteIfNoServer() {
		return serverAccess.current().isEmpty() ? remote : null;
	}

	public ServerInfo serverInfo() {
		RemoteInspector remoteInspector = remoteIfNoServer();
		if (remoteInspector != null) {
			return remoteInspector.serverInfo();
		}
		return serverAccess.call(server -> {
			List<String> dimensions = new ArrayList<>();
			server.getAllLevels().forEach(level -> dimensions.add(level.dimension().identifier().toString()));
			List<String> players = server.getPlayerList().getPlayers().stream().map(p -> p.getName().getString()).toList();
			return new ServerInfo(
					SharedConstants.getCurrentVersion().name(),
					server.isDedicatedServer(),
					server.getTickCount(),
					Snapshots.round(server.getAverageTickTimeNanos() / 1_000_000.0),
					server.tickRateManager().tickrate(),
					server.tickRateManager().isFrozen(),
					dimensions,
					players,
					server.overworld().getOverworldClockTime());
		});
	}

	public PlayerInfo player(Optional<String> name) {
		RemoteInspector remoteInspector = remoteIfNoServer();
		if (remoteInspector != null) {
			return remoteInspector.player(name);
		}
		return serverAccess.call(server -> {
			ServerPlayer player = ServerAccess.resolvePlayer(server, name);
			return describePlayer(player, player.gameMode.getGameModeForPlayer().getName());
		});
	}

	/**
	 * Lists entities around a center (a player's position by default).
	 *
	 * @param center     explicit center, or empty to use the player's position
	 * @param playerName player used as center and dimension source (first online player if empty)
	 * @param radius     search radius in blocks (capped to {@link #MAX_RADIUS})
	 * @param typeFilter optional entity type id filter, e.g. {@code minecraft:zombie} or {@code zombie}
	 * @param limit      maximum number of entries, nearest first
	 */
	public List<EntitySummary> entitiesAround(Optional<Vec> requestedCenter, Optional<String> playerName, int radius,
											  Optional<String> typeFilter, int limit) {
		Optional<Vec3> center = requestedCenter.map(v -> new Vec3(v.x(), v.y(), v.z()));
		int clampedRadius = Math.min(Math.max(radius, 1), MAX_RADIUS);
		Optional<String> normalizedType = typeFilter.map(WorldInspectionService::normalizeId);
		RemoteInspector remoteInspector = remoteIfNoServer();
		if (remoteInspector != null) {
			return remoteInspector.entitiesAround(requestedCenter, playerName, clampedRadius, normalizedType, limit);
		}
		return serverAccess.call(server -> {
			ServerLevel level;
			Vec3 origin;
			if (center.isPresent() && playerName.isEmpty() && server.getPlayerList().getPlayers().isEmpty()) {
				level = server.overworld();
				origin = center.get();
			} else {
				ServerPlayer player = ServerAccess.resolvePlayer(server, playerName);
				level = player.level();
				origin = center.orElse(player.position());
			}
			AABB box = new AABB(origin, origin).inflate(clampedRadius);
			double maxDistanceSqr = (double) clampedRadius * clampedRadius;
			// Multipart sub-entities (dragon wings, tail...) share the parent type: skip them.
			return level.getEntities((Entity) null, box, entity -> !(entity instanceof EnderDragonPart)
							&& entity.position().distanceToSqr(origin) <= maxDistanceSqr
							&& normalizedType.map(type -> type.equals(typeId(entity))).orElse(true))
					.stream()
					.sorted(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(origin)))
					.limit(limit)
					.map(entity -> summarize(entity, origin))
					.toList();
		});
	}

	/**
	 * Full description of one stack of a player's inventory: summary, tooltip and every data component.
	 *
	 * @param playerName player name (first online player if empty)
	 * @param slot       inventory slot (0-8 hotbar, 9-35 storage, 36-39 boots..helmet, 40 offhand); the
	 *                   selected hotbar slot if empty
	 */
	public ItemDetails itemDetails(Optional<String> playerName, java.util.OptionalInt slot) {
		RemoteInspector remoteInspector = remoteIfNoServer();
		if (remoteInspector != null) {
			return remoteInspector.itemDetails(playerName, slot);
		}
		return serverAccess.call(server -> describeItem(ServerAccess.resolvePlayer(server, playerName), slot, server.registryAccess()));
	}

	/**
	 * Describes one stack of a player's inventory (server or local client player).
	 *
	 * @param registries registry access used to serialize the item
	 */
	public static ItemDetails describeItem(Player player, java.util.OptionalInt slot, HolderLookup.Provider registries) {
		Inventory inventory = player.getInventory();
		int index = slot.orElse(inventory.getSelectedSlot());
		if (index < 0 || index >= inventory.getContainerSize()) {
			throw new GameStateException("Slot " + index + " is out of range (0-" + (inventory.getContainerSize() - 1) + ").");
		}
		ItemStack stack = inventory.getItem(index);
		if (stack.isEmpty()) {
			throw new GameStateException("Slot " + index + " of " + player.getName().getString() + " is empty.");
		}
		List<String> tooltip = stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.ADVANCED)
				.stream().map(Component::getString).toList();
		var ops = registries.createSerializationContext(JsonOps.INSTANCE);
		var components = ItemStack.CODEC.encodeStart(ops, stack)
				.resultOrPartial(error -> { throw new GameStateException("Could not serialize the item: " + error); })
				.orElseThrow();
		List<Component> tooltipComponents = stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.ADVANCED);
		ItemLore lore = stack.get(DataComponents.LORE);
		return new ItemDetails(item(index, stack), tooltip,
				tooltipComponents.stream().map(TextFormat::withCodes).toList(),
				lore == null ? List.of() : lore.lines().stream().map(TextFormat::withCodes).toList(),
				components);
	}

	/** Detailed view of an entity found by UUID or numeric network id, in any dimension. */
	public EntityDetails entity(String idOrUuid) {
		RemoteInspector remoteInspector = remoteIfNoServer();
		if (remoteInspector != null) {
			return remoteInspector.entity(idOrUuid);
		}
		return serverAccess.call(server -> {
			Entity entity = findEntity(server, idOrUuid);
			if (entity == null) {
				throw new GameStateException("No loaded entity with id/uuid '" + idOrUuid + "'.");
			}
			return describeEntity(entity);
		});
	}

	// ------------------------------------------------------------------ server-thread helpers

	private static Entity findEntity(MinecraftServer server, String idOrUuid) {
		UUID uuid = parseUuid(idOrUuid);
		Integer numericId = uuid == null ? parseInt(idOrUuid) : null;
		if (uuid == null && numericId == null) {
			throw new GameStateException("'" + idOrUuid + "' is neither a UUID nor a numeric entity id.");
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = uuid != null ? level.getEntity(uuid) : level.getEntity(numericId);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	/** Description of a player (server or local client player); {@code gameMode} is the display name. */
	public static PlayerInfo describePlayer(Player player, String gameMode) {
		Inventory inventory = player.getInventory();
		List<ItemInfo> items = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				items.add(item(slot, stack));
			}
		}
		return new PlayerInfo(
				player.getName().getString(),
				player.getStringUUID(),
				player.level().dimension().identifier().toString(),
				vec(player.position()),
				new Rotation(player.getYRot(), player.getXRot()),
				gameMode,
				player.getHealth(),
				player.getMaxHealth(),
				player.getFoodData().getFoodLevel(),
				player.getFoodData().getSaturationLevel(),
				player.experienceLevel,
				inventory.getSelectedSlot(),
				item(inventory.getSelectedSlot(), inventory.getSelectedItem()),
				items,
				effects(player));
	}

	/** Details of an entity (server or client side). */
	public static EntityDetails describeEntity(Entity entity) {
		List<AttributeInfo> attributes = List.of();
		List<EffectInfo> effects = List.of();
		List<ItemInfo> equipment = new ArrayList<>();
		if (entity instanceof LivingEntity living) {
			attributes = living.getAttributes().getSyncableAttributes().stream()
					.map(instance -> new AttributeInfo(instance.getAttribute().getRegisteredName(),
							Snapshots.round(instance.getBaseValue()), Snapshots.round(instance.getValue()),
							instance.getModifiers().size()))
					.sorted(Comparator.comparing(AttributeInfo::attribute))
					.toList();
			effects = effects(living);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack stack = living.getItemBySlot(slot);
				if (!stack.isEmpty()) {
					equipment.add(item(slot.ordinal(), stack, slot.getName()));
				}
			}
		}
		return new EntityDetails(
				summarize(entity, null),
				new Rotation(entity.getYRot(), entity.getXRot()),
				vec(entity.getDeltaMovement()),
				entity.isAlive(),
				attributes,
				effects,
				equipment);
	}

	/** Summary of an entity; {@code origin} (nullable) is used to compute the distance. */
	public static EntitySummary summarize(Entity entity, Vec3 origin) {
		Float health = entity instanceof LivingEntity living ? living.getHealth() : null;
		Float maxHealth = entity instanceof LivingEntity living ? living.getMaxHealth() : null;
		Double distance = origin == null ? null : Snapshots.round(Math.sqrt(entity.position().distanceToSqr(origin)));
		return new EntitySummary(
				entity.getId(),
				entity.getStringUUID(),
				typeId(entity),
				entity.getName().getString(),
				entity.level().dimension().identifier().toString(),
				vec(entity.position()),
				health,
				maxHealth,
				distance,
				entity.entityTags().stream().sorted().toList());
	}

	private static List<EffectInfo> effects(LivingEntity living) {
		return living.getActiveEffects().stream()
				.map(effect -> new EffectInfo(effect.getEffect().getRegisteredName(), effect.getAmplifier(), effect.getDuration()))
				.toList();
	}

	public static ItemInfo item(int slot, ItemStack stack) {
		return item(slot, stack, null);
	}

	/** Summary of a stack; {@code label} (e.g. an equipment slot name) is appended to the item id if present. */
	private static ItemInfo item(int slot, ItemStack stack, String label) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> loreLines = lore == null || lore.lines().isEmpty()
				? null : lore.lines().stream().map(Component::getString).toList();

		Map<String, Integer> enchantments = null;
		if (!stack.getEnchantments().isEmpty()) {
			enchantments = new LinkedHashMap<>();
			for (var entry : stack.getEnchantments().entrySet()) {
				enchantments.put(entry.getKey().getRegisteredName(), entry.getIntValue());
			}
		}

		boolean damageable = stack.isDamageableItem();
		return new ItemInfo(slot,
				label == null ? itemId(stack) : itemId(stack) + " (" + label + ")",
				stack.getCount(),
				stack.getHoverName().getString(),
				loreLines,
				enchantments,
				damageable ? stack.getMaxDamage() - stack.getDamageValue() : null,
				damageable ? stack.getMaxDamage() : null);
	}

	private static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static String typeId(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	private static Vec vec(Vec3 v) {
		return Vec.of(v.x, v.y, v.z);
	}

	public static String normalizeId(String id) {
		String lower = id.toLowerCase(Locale.ROOT);
		return lower.contains(":") ? lower : "minecraft:" + lower;
	}

	private static UUID parseUuid(String value) {
		try {
			return value.length() == 36 ? UUID.fromString(value) : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Integer parseInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
