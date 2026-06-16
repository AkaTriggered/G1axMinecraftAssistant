package dev.g1ax.agent.tool;

import dev.g1ax.GameOptimizer;
import dev.g1ax.SystemScanner;
import dev.g1ax.agent.MinecraftThread;

/**
 * Tools that expose the existing system/config/diagnostics helpers ({@link GameOptimizer},
 * {@link SystemScanner}) to the agent. File-based reads run off-thread; anything that reads live
 * client state (FPS, loaded world) is marshaled onto the client thread.
 *
 * {@code write_config} is the one mutating tool here — it is marked as an action so it honors the
 * agent's safe-mode guard, and it always auto-backs-up via {@link GameOptimizer#writeConfig}.
 */
public final class SystemTools {
    private SystemTools() {}

    public static void register(ToolRegistry reg) {
        reg.register(new SimpleTool(
                "get_performance",
                "Get performance and hardware info: FPS, RAM used/total, CPU cores, render distance, "
                        + "graphics mode, plus live entity count.",
                SchemaBuilder.empty(),
                args -> MinecraftThread.call(() ->
                        GameOptimizer.getSystemInfo() + "\nWorld: " + SystemScanner.scan_world_performance())));

        reg.register(new SimpleTool(
                "list_mods",
                "List all installed mods with their versions.",
                SchemaBuilder.empty(),
                args -> GameOptimizer.getInstalledMods()));

        reg.register(new SimpleTool(
                "list_configs",
                "List available mod config files (json/properties/toml) under the config directory.",
                SchemaBuilder.empty(),
                args -> GameOptimizer.listAllConfigs()));

        reg.register(new SimpleTool(
                "read_config",
                "Read the full contents of a config file by its path relative to the config folder, "
                        + "e.g. 'sodium-options.json' or 'iris.properties'.",
                SchemaBuilder.object()
                        .stringProp("path", "Config path relative to the config/ directory", true)
                        .build(),
                args -> {
                    String path = SimpleTool.str(args, "path", "").trim();
                    if (path.isEmpty()) return "ERROR: 'path' is required.";
                    return GameOptimizer.readConfig(path);
                },
                false,
                args -> "read_config: " + SimpleTool.str(args, "path", "?")));

        reg.register(new SimpleTool(
                "write_config",
                "Overwrite a config file with new full contents. Automatically creates a timestamped "
                        + "backup first and never deletes files. This is an ACTION: in safe mode it is "
                        + "previewed, not applied.",
                SchemaBuilder.object()
                        .stringProp("path", "Config path relative to config/ directory", true)
                        .stringProp("content", "The complete new file contents to write", true)
                        .build(),
                args -> {
                    String path = SimpleTool.str(args, "path", "").trim();
                    String content = SimpleTool.str(args, "content", "");
                    if (path.isEmpty()) return "ERROR: 'path' is required.";
                    return GameOptimizer.writeConfig(path, content);
                },
                true,
                args -> "write_config: " + SimpleTool.str(args, "path", "?")));

        reg.register(new SimpleTool(
                "scan_logs",
                "Summarize recent ERROR/WARN entries in logs/latest.log.",
                SchemaBuilder.empty(),
                args -> SystemScanner.scan_latest_log()));

        reg.register(new SimpleTool(
                "scan_crashes",
                "Get the most recent crash report's primary error, if any crash reports exist.",
                SchemaBuilder.empty(),
                args -> SystemScanner.scan_crash_reports()));
    }
}
