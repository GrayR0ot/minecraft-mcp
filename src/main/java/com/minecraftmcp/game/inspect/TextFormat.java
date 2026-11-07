package com.minecraftmcp.game.inspect;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Renders a {@link Component} with its colors and styles as text using legacy-style codes, so the
 * AI (and the developer reading it) can see what a plugin set: {@code &c} red, {@code &l} bold,
 * {@code &o} italic, {@code &n} underline, {@code &m} strikethrough, {@code &k} obfuscated,
 * {@code &r} reset, and {@code &#RRGGBB} for hex colors.
 */
public final class TextFormat {

	private TextFormat() {
	}

	/** Component as plain text with {@code &} formatting codes. Unstyled text has no codes. */
	public static String withCodes(Component component) {
		StringBuilder out = new StringBuilder();
		Style[] previous = {Style.EMPTY};
		component.visit((style, text) -> {
			if (!text.isEmpty()) {
				if (!style.equals(previous[0]) && !(style.isEmpty() && previous[0].isEmpty())) {
					out.append(codes(style));
					previous[0] = style;
				}
				out.append(text);
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	private static String codes(Style style) {
		StringBuilder codes = new StringBuilder("&r");
		TextColor color = style.getColor();
		if (color != null) {
			codes.append(colorCode(color));
		}
		if (style.isBold()) codes.append("&l");
		if (style.isItalic()) codes.append("&o");
		if (style.isUnderlined()) codes.append("&n");
		if (style.isStrikethrough()) codes.append("&m");
		if (style.isObfuscated()) codes.append("&k");
		return codes.toString();
	}

	private static String colorCode(TextColor color) {
		for (ChatFormatting formatting : ChatFormatting.values()) {
			if (color.equals(TextColor.fromLegacyFormat(formatting))) {
				return "&" + formatting.toString().substring(1);
			}
		}
		return String.format("&#%06X", color.getValue() & 0xFFFFFF);
	}
}
