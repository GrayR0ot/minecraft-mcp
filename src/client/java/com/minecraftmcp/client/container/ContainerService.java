package com.minecraftmcp.client.container;

import com.minecraftmcp.client.ClientAccess;
import com.minecraftmcp.game.GameStateException;
import com.minecraftmcp.game.inspect.Snapshots.ItemInfo;
import com.minecraftmcp.game.inspect.TextFormat;
import com.minecraftmcp.game.inspect.WorldInspectionService;
import com.minecraftmcp.mixin.client.AbstractContainerScreenAccessor;
import com.minecraftmcp.mixin.client.MouseHandlerAccessor;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives container screens (player inventory, chests, plugin menus) without a real mouse: lists
 * slots, moves the virtual cursor over a slot so the screen draws its tooltip (visible with
 * {@code take_screenshot}), and performs slot clicks through vanilla's container packets.
 */
public final class ContainerService {

	/** Time given to the render thread to draw a frame with the new cursor position. */
	private static final long RENDER_WAIT_MS = 250;

	private final ClientAccess client;

	public ContainerService(ClientAccess client) {
		this.client = client;
	}

	/** Kinds of slot click. */
	public enum Click {
		/** Pick up / place the whole stack. */
		LEFT,
		/** Pick up half / place one. */
		RIGHT,
		/** Shift-click: move the stack to the other part of the container. */
		SHIFT,
		/** Middle click (creative: copy the stack). */
		MIDDLE,
		/** Drop one item (Q). */
		DROP,
		/** Drop the whole stack (Ctrl+Q). */
		DROP_STACK,
		/** Swap with a hotbar slot (number key). */
		SWAP
	}

	/**
	 * State of the open container screen.
	 *
	 * @param screen  screen class name
	 * @param title   screen title (e.g. a plugin menu name)
	 * @param slots   non-empty slots; {@code slot} is the container slot index to use with hover/click
	 * @param carried stack held by the cursor, or null
	 */
	public record ContainerState(String screen, String title, List<ItemInfo> slots, ItemInfo carried) {
	}

	/**
	 * Result of a hover.
	 *
	 * @param slot             slot index
	 * @param item             the stack there, or null if empty
	 * @param tooltip          tooltip lines as plain text
	 * @param tooltipFormatted same lines with colors and styles as &amp;-codes
	 */
	public record HoverResult(int slot, ItemInfo item, List<String> tooltip, List<String> tooltipFormatted) {
	}

	/** Opens the player inventory screen (E key). */
	public ContainerState openInventory() {
		ContainerState state = client.call(minecraft -> {
			if (minecraft.player == null) {
				throw new GameStateException("Not in a world. Join one first.");
			}
			minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
			return describe(requireContainerScreen(minecraft));
		});
		sleepForFrame();
		return state;
	}

	/** Describes the open container screen and its non-empty slots. */
	public ContainerState state() {
		return client.call(minecraft -> describe(requireContainerScreen(minecraft)));
	}

	/** Moves the cursor to the center of a slot; the screen then draws its hover highlight and tooltip. */
	public HoverResult hover(int slotIndex) {
		HoverResult result = client.call(minecraft -> {
			AbstractContainerScreen<?> screen = requireContainerScreen(minecraft);
			Slot slot = requireSlot(screen.getMenu(), slotIndex);
			Window window = minecraft.getWindow();
			double guiX = ((AbstractContainerScreenAccessor) screen).mcpMod$leftPos() + slot.x + 8;
			double guiY = ((AbstractContainerScreenAccessor) screen).mcpMod$topPos() + slot.y + 8;
			MouseHandlerAccessor mouse = (MouseHandlerAccessor) minecraft.mouseHandler;
			mouse.mcpMod$setXpos(guiX * window.getScreenWidth() / window.getGuiScaledWidth());
			mouse.mcpMod$setYpos(guiY * window.getScreenHeight() / window.getGuiScaledHeight());

			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				return new HoverResult(slotIndex, null, List.of(), List.of());
			}
			List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL);
			return new HoverResult(slotIndex, WorldInspectionService.item(slotIndex, stack),
					lines.stream().map(Component::getString).toList(),
					lines.stream().map(TextFormat::withCodes).toList());
		});
		sleepForFrame();
		return result;
	}

	/**
	 * Clicks a slot.
	 *
	 * @param slotIndex container slot index, or -999 to click outside the window (drops the carried stack)
	 * @param hotbar    hotbar slot 0-8, only for {@link Click#SWAP}
	 */
	public ContainerState click(int slotIndex, Click click, int hotbar) {
		ContainerState state = client.call(minecraft -> {
			AbstractContainerScreen<?> screen = requireContainerScreen(minecraft);
			AbstractContainerMenu menu = screen.getMenu();
			if (slotIndex != -999) {
				requireSlot(menu, slotIndex);
			}
			int button = switch (click) {
				case LEFT, SHIFT, DROP -> 0;
				case RIGHT, DROP_STACK -> 1;
				case MIDDLE -> 2;
				case SWAP -> hotbar;
			};
			ContainerInput input = switch (click) {
				case LEFT, RIGHT -> ContainerInput.PICKUP;
				case SHIFT -> ContainerInput.QUICK_MOVE;
				case MIDDLE -> ContainerInput.CLONE;
				case DROP, DROP_STACK -> ContainerInput.THROW;
				case SWAP -> ContainerInput.SWAP;
			};
			minecraft.gameMode.handleContainerInput(menu.containerId, slotIndex, button, input, minecraft.player);
			return describe(screen);
		});
		sleepForFrame();
		return state;
	}

	private static ContainerState describe(AbstractContainerScreen<?> screen) {
		AbstractContainerMenu menu = screen.getMenu();
		List<ItemInfo> slots = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (!slot.getItem().isEmpty()) {
				slots.add(WorldInspectionService.item(slot.index, slot.getItem()));
			}
		}
		ItemStack carried = menu.getCarried();
		return new ContainerState(screen.getClass().getSimpleName(), screen.getTitle().getString(), slots,
				carried.isEmpty() ? null : WorldInspectionService.item(-1, carried));
	}

	private static AbstractContainerScreen<?> requireContainerScreen(Minecraft minecraft) {
		Screen screen = minecraft.gui.screen();
		if (screen instanceof AbstractContainerScreen<?> container) {
			return container;
		}
		throw new GameStateException("No container screen is open (current screen: "
				+ (screen == null ? "none" : screen.getClass().getSimpleName())
				+ "). Use action=open_inventory, or open a menu with run_command / client_input.");
	}

	private static Slot requireSlot(AbstractContainerMenu menu, int index) {
		if (index < 0 || index >= menu.slots.size()) {
			throw new GameStateException("Slot " + index + " does not exist in this container (0-" + (menu.slots.size() - 1) + ").");
		}
		return menu.getSlot(index);
	}

	private static void sleepForFrame() {
		try {
			Thread.sleep(RENDER_WAIT_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GameStateException("Interrupted while waiting for the screen to render.", e);
		}
	}
}
