package com.minecraftmcp.game.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Log4j appender keeping the latest log events in a ring buffer, so the AI can read logs with a
 * cursor ({@code afterId}) instead of parsing {@code latest.log}.
 *
 * <p>Thread-safe: log events arrive from any thread, queries come from MCP worker threads.
 */
public final class LogCapture extends AbstractAppender {

	private static final int MAX_MESSAGE_CHARS = 4_000;
	private static final int MAX_STACK_CHARS = 6_000;

	private final int capacity;
	private final ArrayDeque<LogEntry> buffer;
	private long nextId = 1;

	/**
	 * One captured log line.
	 *
	 * @param id        monotonically increasing cursor
	 * @param time      ISO-8601 timestamp
	 * @param level     log level name
	 * @param logger    logger name
	 * @param thread    thread name
	 * @param message   formatted message (truncated if huge)
	 * @param throwable stack trace, if any (truncated)
	 */
	public record LogEntry(long id, String time, String level, String logger, String thread, String message, String throwable) {
	}

	/**
	 * A page of results.
	 *
	 * @param entries matching entries, oldest first
	 * @param nextId  cursor to pass as {@code afterId} to get only newer entries
	 * @param dropped {@code true} if entries newer than {@code afterId} were evicted from the buffer
	 */
	public record LogPage(List<LogEntry> entries, long nextId, boolean dropped) {
	}

	private LogCapture(int capacity) {
		super("McpLogCapture", null, null, true, Property.EMPTY_ARRAY);
		this.capacity = capacity;
		this.buffer = new ArrayDeque<>(capacity);
	}

	/** Creates the appender and attaches it to the root logger of the game's logging context. */
	public static LogCapture install(int capacity) {
		LogCapture appender = new LogCapture(capacity);
		appender.start();
		Logger root = (Logger) LogManager.getRootLogger();
		LoggerContext context = root.getContext();
		Configuration configuration = context.getConfiguration();
		configuration.addAppender(appender);
		configuration.getRootLogger().addAppender(appender, Level.ALL, null);
		context.updateLoggers();
		return appender;
	}

	@Override
	public void append(LogEvent event) {
		String message = truncate(event.getMessage() == null ? "" : event.getMessage().getFormattedMessage(), MAX_MESSAGE_CHARS);
		String throwable = event.getThrown() == null ? null : truncate(stackTrace(event.getThrown()), MAX_STACK_CHARS);
		String time = Instant.ofEpochMilli(event.getTimeMillis()).toString();
		synchronized (buffer) {
			if (buffer.size() >= capacity) {
				buffer.removeFirst();
			}
			buffer.addLast(new LogEntry(nextId++, time, event.getLevel().name(), event.getLoggerName(),
					event.getThreadName(), message, throwable));
		}
	}

	/**
	 * Queries the buffer.
	 *
	 * @param afterId      only entries with {@code id > afterId}; 0 for everything
	 * @param minLevel     minimum level (e.g. {@code WARN}); empty for all
	 * @param contains     case-insensitive substring of message or stack trace
	 * @param loggerFilter case-insensitive substring of the logger name
	 * @param limit        max entries; when more match, the newest ones are returned
	 */
	public LogPage query(long afterId, Optional<String> minLevel, Optional<String> contains, Optional<String> loggerFilter, int limit) {
		Level threshold = minLevel.map(name -> Level.toLevel(name.toUpperCase(Locale.ROOT), Level.ALL)).orElse(Level.ALL);
		Optional<String> needle = contains.map(s -> s.toLowerCase(Locale.ROOT));
		Optional<String> loggerNeedle = loggerFilter.map(s -> s.toLowerCase(Locale.ROOT));

		List<LogEntry> matches = new ArrayList<>();
		long next;
		boolean dropped;
		synchronized (buffer) {
			next = nextId - 1;
			dropped = !buffer.isEmpty() && afterId > 0 && buffer.peekFirst().id() > afterId + 1;
			for (LogEntry entry : buffer) {
				if (entry.id() <= afterId) continue;
				if (!Level.toLevel(entry.level()).isMoreSpecificThan(threshold)) continue;
				if (loggerNeedle.isPresent() && !entry.logger().toLowerCase(Locale.ROOT).contains(loggerNeedle.get())) continue;
				if (needle.isPresent() && !matchesText(entry, needle.get())) continue;
				matches.add(entry);
			}
		}
		if (matches.size() > limit) {
			matches = new ArrayList<>(matches.subList(matches.size() - limit, matches.size()));
		}
		return new LogPage(List.copyOf(matches), next, dropped);
	}

	private static boolean matchesText(LogEntry entry, String needle) {
		return entry.message().toLowerCase(Locale.ROOT).contains(needle)
				|| (entry.throwable() != null && entry.throwable().toLowerCase(Locale.ROOT).contains(needle));
	}

	private static String stackTrace(Throwable throwable) {
		StringWriter writer = new StringWriter();
		throwable.printStackTrace(new PrintWriter(writer));
		return writer.toString();
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max) + "... [truncated]";
	}
}
