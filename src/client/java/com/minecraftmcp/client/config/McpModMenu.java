package com.minecraftmcp.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu entrypoint: adds a "Configure" button when Cloth Config is also installed. Both
 * libraries are optional; this class is only loaded by Mod Menu itself.
 */
public final class McpModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
			return ModMenuApi.super.getModConfigScreenFactory();
		}
		return McpConfigScreen::create;
	}
}
