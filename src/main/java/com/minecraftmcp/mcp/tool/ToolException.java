package com.minecraftmcp.mcp.tool;

/**
 * Expected failure of a tool (bad argument, missing player, no world loaded...).
 *
 * <p>The message is sent verbatim to the AI as an {@code isError} result, so it should explain
 * what went wrong and, ideally, how to fix the call.
 */
public class ToolException extends RuntimeException {

	public ToolException(String message) {
		super(message);
	}

	public ToolException(String message, Throwable cause) {
		super(message, cause);
	}
}
