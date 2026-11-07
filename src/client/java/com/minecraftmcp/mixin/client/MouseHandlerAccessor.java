package com.minecraftmcp.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the AI place the (virtual) mouse cursor, so an open screen renders the hover state and
 * tooltip of the slot under it exactly as it does for a player.
 *
 * <p>Accessor only: no behaviour changes. Methods are prefixed with {@code mcpMod$}.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

	/** Cursor X in window coordinates. */
	@Accessor("xpos")
	void mcpMod$setXpos(double xpos);

	/** Cursor Y in window coordinates. */
	@Accessor("ypos")
	void mcpMod$setYpos(double ypos);
}
