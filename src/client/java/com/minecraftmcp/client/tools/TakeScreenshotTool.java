package com.minecraftmcp.client.tools;

import com.minecraftmcp.client.screenshot.ScreenshotService;
import com.minecraftmcp.client.screenshot.ScreenshotService.Capture;
import com.minecraftmcp.mcp.tool.McpTool;
import com.minecraftmcp.mcp.tool.Schema;
import com.minecraftmcp.mcp.tool.ToolArgs;
import com.minecraftmcp.mcp.tool.ToolContent;
import com.minecraftmcp.mcp.tool.ToolDescriptor;
import com.minecraftmcp.mcp.tool.ToolResult;

import java.util.List;

/** Returns a PNG of what the player currently sees. */
public final class TakeScreenshotTool implements McpTool {

	private final ScreenshotService screenshots;
	private final int defaultMaxWidth;
	private final ToolDescriptor descriptor;

	public TakeScreenshotTool(ScreenshotService screenshots, int defaultMaxWidth) {
		this.screenshots = screenshots;
		this.defaultMaxWidth = defaultMaxWidth;
		this.descriptor = new ToolDescriptor(
				"take_screenshot",
				"Take screenshot",
				"Captures the game window exactly as the player sees it (world, HUD, open screens) and returns it "
						+ "as a PNG image. Use it to check visuals: particles, HUD, GUIs, mob placement. The image is "
						+ "downscaled to at most 'max_width' pixels wide (default " + defaultMaxWidth + ").",
				Schema.object()
						.integer("max_width", "Maximum width in pixels, 128-4096.", false)
						.build(),
				true, false);
	}

	@Override
	public ToolDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public ToolResult call(ToolArgs args) {
		Capture capture = screenshots.capture(args.intOr("max_width", defaultMaxWidth, 128, 4096));
		String caption = "Screenshot " + capture.width() + "x" + capture.height()
				+ " (window " + capture.sourceWidth() + "x" + capture.sourceHeight() + ")";
		return new ToolResult(List.of(new ToolContent.Image(capture.png(), "image/png"), new ToolContent.Text(caption)), false);
	}
}
