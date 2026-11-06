package com.minecraftmcp.client.chat;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

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
	 */
	public record ChatLine(long id, String time, String kind, String sender, String text) {
	}

	public ChatCapture(int capacity) {
		this.capacity = capacity;
		this.lines = new ArrayDeque<>(capacity);
	}

	/** Subscribes to Fabric's client message events. Call once from the client initializer. */
	public void registerEvents() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) ->
				add(overlay ? "actionbar" : "system", null, message.getString()));
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				add("chat", sender == null ? null : sender.name(), message.getString()));
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

	private void add(String kind, String sender, String text) {
		synchronized (lines) {
			if (lines.size() >= capacity) {
				lines.removeFirst();
			}
			lines.addLast(new ChatLine(nextId++, Instant.now().toString(), kind, sender, text));
		}
	}
}
