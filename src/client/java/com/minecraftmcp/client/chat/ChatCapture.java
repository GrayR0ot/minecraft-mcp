package com.minecraftmcp.client.chat;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Remembers the latest chat and system messages received by the client (command feedback, death
 * messages, plugin notifications...), so the AI can read what a human would read in the chat box.
 */
public final class ChatCapture {

	private final int capacity;
	private final ArrayDeque<ChatLine> lines;
	private long nextId = 1;

	/**
	 * A received message.
	 *
	 * @param id      monotonically increasing cursor
	 * @param time    ISO-8601 timestamp
	 * @param kind    {@code chat}, {@code system} or {@code actionbar}
	 * @param sender  sender name for player chat, otherwise {@code null}
	 * @param text    plain text content
	 * @param key     translation key for vanilla messages (e.g. {@code command.unknown.command}), otherwise {@code null}
	 * @param error   {@code true} if the message is rendered in red (the convention for errors in vanilla, WorldEdit
	 *                and most plugins), otherwise {@code null}
	 */
	public record ChatLine(long id, String time, String kind, String sender, String text, String key, Boolean error) {
	}

	public ChatCapture(int capacity) {
		this.capacity = capacity;
		this.lines = new ArrayDeque<>(capacity);
	}

	/** Subscribes to Fabric's client message events. Call once from the client initializer. */
	public void registerEvents() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) ->
				add(overlay ? "actionbar" : "system", null, message.getString(),
						message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null,
						isRed(message) ? Boolean.TRUE : null));
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				add("chat", sender == null ? null : sender.name(), message.getString(), null, null));
	}

	/** Messages with {@code id > afterId}, at most {@code limit} (newest kept). */
	public List<ChatLine> since(long afterId, int limit) {
		List<ChatLine> result = new ArrayList<>();
		synchronized (lines) {
			for (ChatLine line : lines) {
				if (line.id() > afterId) {
					result.add(line);
				}
			}
		}
		return result.size() > limit ? List.copyOf(result.subList(result.size() - limit, result.size())) : List.copyOf(result);
	}

	/** Id of the newest message so far (0 if none): pass it to {@link #since} to read what comes after. */
	public long lastId() {
		synchronized (lines) {
			return nextId - 1;
		}
	}

	/** Whether the component, or any part of it, is red or dark red. */
	private static boolean isRed(Component component) {
		TextColor color = component.getStyle().getColor();
		if (TextColor.RED.equals(color) || TextColor.DARK_RED.equals(color)) {
			return true;
		}
		return component.getSiblings().stream().anyMatch(ChatCapture::isRed);
	}

	private void add(String kind, String sender, String text, String key, Boolean error) {
		synchronized (lines) {
			if (lines.size() >= capacity) {
				lines.removeFirst();
			}
			lines.addLast(new ChatLine(nextId++, Instant.now().toString(), kind, sender, text, key, error));
		}
	}
}
