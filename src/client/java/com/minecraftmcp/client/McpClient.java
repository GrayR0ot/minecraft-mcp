package com.minecraftmcp.client;

import com.minecraftmcp.client.chat.ChatCapture;
import com.minecraftmcp.client.command.RemoteCommandService;
import com.minecraftmcp.client.command.RemoteInspectionService;
import com.minecraftmcp.client.input.InputService;
import com.minecraftmcp.client.screenshot.ScreenshotService;
import com.minecraftmcp.client.container.ContainerService;
import com.minecraftmcp.client.tools.ClientContainerTool;
import com.minecraftmcp.client.tools.ClientInputTool;
import com.minecraftmcp.client.tools.ClientStateTool;
import com.minecraftmcp.client.tools.ClientWorldTool;
import com.minecraftmcp.client.tools.ListCommandsTool;
import com.minecraftmcp.client.command.CommandCatalogService;
import com.minecraftmcp.client.tools.TakeScreenshotTool;
import com.minecraftmcp.client.world.WorldSessionService;
import com.minecraftmcp.core.McpMod;
import com.minecraftmcp.core.McpRuntime;
import com.minecraftmcp.core.config.McpConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Client entrypoint: adds the client-only MCP tools (screenshot, client state, input, world
 * management) to the shared registry and ties the MCP endpoint lifetime to the game window.
 */
public final class McpClient implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("MCP");

	@Override
	public void onInitializeClient() {
		McpRuntime runtime = McpMod.runtime();
		McpConfig config = runtime.config();

		ClientAccess client = new ClientAccess(Duration.ofMillis(config.server.gameThreadTimeoutMs));
		ChatCapture chat = new ChatCapture(config.client.chatBufferSize);
		chat.registerEvents();

		RemoteCommandService remoteCommands = new RemoteCommandService(client, chat, runtime.commands().policy());
		runtime.commands().setRemoteExecutor(remoteCommands);
		runtime.inspection().setRemoteInspector(new RemoteInspectionService(client));

		InputService input = new InputService(client, remoteCommands::guard);
		input.registerEvents();

		runtime.tools().registerAll(List.of(
				new TakeScreenshotTool(new ScreenshotService(client), config.client.screenshotMaxWidth),
				new ClientStateTool(new ClientStateService(client, chat)),
				new ClientInputTool(input),
				new ClientContainerTool(new ContainerService(client)),
				new ListCommandsTool(new CommandCatalogService(client)),
				new ClientWorldTool(new WorldSessionService(client, runtime.serverAccess()))));

		ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
			if (config.client.disablePauseOnLostFocus) {
				// Runtime-only: lets the AI drive the game while the window is in the background.
				minecraft.options.pauseOnLostFocus = false;
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> runtime.shutdown());
		LOGGER.info("Minecraft MCP client tools registered ({} tools total)", runtime.tools().size());
	}
}
