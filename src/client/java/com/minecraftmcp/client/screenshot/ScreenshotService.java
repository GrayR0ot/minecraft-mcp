package com.minecraftmcp.client.screenshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.minecraftmcp.client.ClientAccess;
import com.minecraftmcp.game.GameStateException;
import net.minecraft.client.Screenshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

/**
 * Captures the main framebuffer (exactly what the player sees, HUD included) as a PNG.
 *
 * <p>The GPU read-back is asynchronous: vanilla delivers the image on a later frame, on the render
 * thread. Pixels are copied there and PNG encoding happens on the calling (MCP worker) thread.
 */
public final class ScreenshotService {

	private final ClientAccess client;

	public ScreenshotService(ClientAccess client) {
		this.client = client;
	}

	/**
	 * A captured frame.
	 *
	 * @param png          PNG bytes
	 * @param width        image width after downscaling
	 * @param height       image height after downscaling
	 * @param sourceWidth  framebuffer width
	 * @param sourceHeight framebuffer height
	 */
	public record Capture(byte[] png, int width, int height, int sourceWidth, int sourceHeight) {
	}

	private record RawFrame(int[] argb, int width, int height, int sourceWidth, int sourceHeight) {
	}

	/**
	 * Takes a screenshot, scaled down (aspect ratio kept) so that it is at most
	 * {@code maxWidth} pixels wide.
	 */
	public Capture capture(int maxWidth) {
		CompletableFuture<RawFrame> frame = new CompletableFuture<>();
		client.post(minecraft -> {
			try {
				RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
				int sourceWidth = target.width;
				int sourceHeight = target.height;
				// Always read back at full size: vanilla's own downscaling throws when the size is not divisible by the
				// factor (e.g. 2560 px wide with factor 3). We resize ourselves afterwards.
				Screenshot.takeScreenshot(target, 1, image -> {
					try (image) {
						frame.complete(new RawFrame(image.getPixels(), image.getWidth(), image.getHeight(), sourceWidth, sourceHeight));
					} catch (RuntimeException e) {
						frame.completeExceptionally(e);
					}
				});
			} catch (RuntimeException e) {
				frame.completeExceptionally(e);
			}
		});
		RawFrame raw = ClientAccess.await(frame, client.timeout());
		if (raw.width() <= 0 || raw.height() <= 0) {
			throw new GameStateException("Empty framebuffer (window minimized?).");
		}
		BufferedImage image = new BufferedImage(raw.width(), raw.height(), BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, raw.width(), raw.height(), raw.argb(), 0, raw.width());
		image = fitWidth(image, maxWidth);
		return new Capture(encodePng(image), image.getWidth(), image.getHeight(), raw.sourceWidth(), raw.sourceHeight());
	}

	/** Scales the image down (area averaging, aspect ratio kept) so it is at most {@code maxWidth} wide. */
	private static BufferedImage fitWidth(BufferedImage image, int maxWidth) {
		if (image.getWidth() <= maxWidth) {
			return image;
		}
		int width = maxWidth;
		int height = Math.max(1, Math.round(image.getHeight() * (maxWidth / (float) image.getWidth())));
		java.awt.Image scaled = image.getScaledInstance(width, height, java.awt.Image.SCALE_AREA_AVERAGING);
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D graphics = result.createGraphics();
		try {
			graphics.drawImage(scaled, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	private static byte[] encodePng(BufferedImage image) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("PNG encoding failed", e);
		}
	}
}
