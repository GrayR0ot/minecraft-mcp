package com.minecraftmcp.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes where a container screen is drawn, to convert a slot position to screen coordinates.
 *
 * <p>Accessor only: no behaviour changes. Methods are prefixed with {@code mcpMod$}.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

	@Accessor("leftPos")
	int mcpMod$leftPos();

	@Accessor("topPos")
	int mcpMod$topPos();
}
