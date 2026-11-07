package com.minecraftmcp.client.config;

import com.minecraftmcp.core.McpMod;
import com.minecraftmcp.core.config.ConfigLoader;
import com.minecraftmcp.core.config.McpConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Cloth Config screen for {@code config/minecraft_mcp.json}, opened from Mod Menu. It edits the
 * file as stored (environment overrides such as MCP_TOKEN are never written back). Changes apply
 * the next time the game starts.
 */
final class McpConfigScreen {

	private static final String PREFIX = "config." + McpMod.MOD_ID + ".";

	private McpConfigScreen() {
	}

	private static Component t(String key) {
		return Component.translatable(PREFIX + key);
	}

	static Screen create(Screen parent) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(McpMod.MOD_ID + ".json");
		McpConfig config = ConfigLoader.readFile(file);

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(t("title"))
				.setSavingRunnable(() -> ConfigLoader.save(file, config.normalize()));
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory server = builder.getOrCreateCategory(t("category.server"));
		server.addEntry(entries.startTextDescription(t("restart")).build());
		server.addEntry(entries.startBooleanToggle(t("server.enabled"), config.server.enabled)
				.setDefaultValue(true)
				.setTooltip(t("server.enabled.tooltip"))
				.setSaveConsumer(value -> config.server.enabled = value)
				.requireRestart().build());
		server.addEntry(entries.startStrField(t("server.host"), config.server.host)
				.setDefaultValue("127.0.0.1")
				.setTooltip(t("server.host.tooltip"))
				.setSaveConsumer(value -> config.server.host = value)
				.requireRestart().build());
		server.addEntry(entries.startIntField(t("server.port"), config.server.port)
				.setDefaultValue(25580).setMin(0).setMax(65_535)
				.setSaveConsumer(value -> config.server.port = value)
				.requireRestart().build());
		server.addEntry(entries.startStrField(t("server.authToken"), config.server.authToken)
				.setDefaultValue("")
				.setTooltip(t("server.authToken.tooltip"))
				.setSaveConsumer(value -> config.server.authToken = value.strip())
				.requireRestart().build());
		server.addEntry(entries.startIntField(t("server.gameThreadTimeoutMs"), config.server.gameThreadTimeoutMs)
				.setDefaultValue(10_000).setMin(500).setMax(120_000)
				.setTooltip(t("server.gameThreadTimeoutMs.tooltip"))
				.setSaveConsumer(value -> config.server.gameThreadTimeoutMs = value)
				.requireRestart().build());

		ConfigCategory commands = builder.getOrCreateCategory(t("category.commands"));
		commands.addEntry(entries.startBooleanToggle(t("commands.restricted"), config.restrictedCommands())
				.setDefaultValue(true)
				.setTooltip(t("commands.restricted.tooltip"))
				.setSaveConsumer(value -> config.commands.mode = value ? "RESTRICTED" : "UNRESTRICTED")
				.requireRestart().build());
		commands.addEntry(entries.startStrList(t("commands.denied"), config.commands.denied)
				.setDefaultValue(new McpConfig().commands.denied)
				.setTooltip(t("commands.denied.tooltip"))
				.setSaveConsumer(value -> config.commands.denied = new ArrayList<>(value.stream()
						.map(name -> name.strip().toLowerCase(Locale.ROOT))
						.filter(name -> !name.isEmpty())
						.toList()))
				.requireRestart().build());

		ConfigCategory other = builder.getOrCreateCategory(t("category.other"));
		other.addEntry(entries.startIntField(t("logs.bufferSize"), config.logs.bufferSize)
				.setDefaultValue(2_000).setMin(100).setMax(50_000)
				.setSaveConsumer(value -> config.logs.bufferSize = value)
				.requireRestart().build());
		other.addEntry(entries.startBooleanToggle(t("client.disablePauseOnLostFocus"), config.client.disablePauseOnLostFocus)
				.setDefaultValue(true)
				.setTooltip(t("client.disablePauseOnLostFocus.tooltip"))
				.setSaveConsumer(value -> config.client.disablePauseOnLostFocus = value)
				.requireRestart().build());
		other.addEntry(entries.startIntField(t("client.screenshotMaxWidth"), config.client.screenshotMaxWidth)
				.setDefaultValue(960).setMin(128).setMax(4096)
				.setTooltip(t("client.screenshotMaxWidth.tooltip"))
				.setSaveConsumer(value -> config.client.screenshotMaxWidth = value)
				.requireRestart().build());
		other.addEntry(entries.startIntField(t("client.chatBufferSize"), config.client.chatBufferSize)
				.setDefaultValue(200).setMin(10).setMax(5_000)
				.setSaveConsumer(value -> config.client.chatBufferSize = value)
				.requireRestart().build());

		return builder.build();
	}
}
