package com.minecraftmcp.mcp.protocol;

/**
 * Identity of this MCP server, returned by {@code initialize}.
 *
 * @param name         machine name, e.g. {@code minecraft-mcp}
 * @param title        human-readable name
 * @param version      mod version
 * @param instructions usage hints sent to the AI client at connection time
 */
public record McpServerInfo(String name, String title, String version, String instructions) {
}
