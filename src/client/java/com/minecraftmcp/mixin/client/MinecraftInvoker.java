package com.minecraftmcp.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private methods behind the right and left mouse buttons, so the AI can trigger
 * exactly the vanilla "use" and "attack" logic (block/entity/air targeting, cooldowns, packets)
 * instead of re-implementing it.
 *
 * <p>Invoker only: no behaviour of {@link Minecraft} is changed. Methods are prefixed with
 * {@code mcpMod$} to avoid clashes with other mods.
 */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {

	/** Same as pressing the "use item" key once. */
	@Invoker("startUseItem")
	void mcpMod$startUseItem();

	/**
	 * Same as pressing the "attack" key once.
	 *
	 * @return {@code true} if the hand swung
	 */
	@Invoker("startAttack")
	boolean mcpMod$startAttack();
}
