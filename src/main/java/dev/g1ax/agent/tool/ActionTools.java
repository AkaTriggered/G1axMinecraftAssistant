package dev.g1ax.agent.tool;

import dev.g1ax.agent.MinecraftThread;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.function.BooleanSupplier;

/**
 * The tools that let the agent act in the world: send chat and run commands as the player.
 * Both are marked as actions, so the agent's safe-mode guard previews them unless the user has
 * enabled trust. Independently, config switches ({@code allowCommands}/{@code allowChat}) can hard-
 * disable either capability regardless of trust.
 */
public final class ActionTools {
    private ActionTools() {}

    public static void register(ToolRegistry reg, BooleanSupplier allowCommands, BooleanSupplier allowChat) {
        reg.register(new SimpleTool(
                "run_command",
                "Run a Minecraft command as the player (e.g. 'give @s minecraft:iron_ingot 3', "
                        + "'time set day', 'tp ...'). Provide the command WITHOUT a leading slash. "
                        + "Requires cheats/permission on the server. This is an ACTION.",
                SchemaBuilder.object()
                        .stringProp("command", "The command to run, without the leading '/'", true)
                        .build(),
                args -> {
                    if (!allowCommands.getAsBoolean()) {
                        return "Command execution is disabled in config (allowCommands=false).";
                    }
                    String cmd = stripSlash(SimpleTool.str(args, "command", "").trim());
                    if (cmd.isEmpty()) return "ERROR: 'command' is required.";
                    return MinecraftThread.call(() -> {
                        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
                        if (h == null) return "ERROR: not connected to a world/server.";
                        h.sendChatCommand(cmd);
                        return "Executed: /" + cmd;
                    });
                },
                true,
                args -> "run_command: /" + stripSlash(SimpleTool.str(args, "command", "?"))));

        reg.register(new SimpleTool(
                "send_chat",
                "Send a public chat message as the player. This is an ACTION.",
                SchemaBuilder.object()
                        .stringProp("message", "The chat message to send", true)
                        .build(),
                args -> {
                    if (!allowChat.getAsBoolean()) {
                        return "Sending chat is disabled in config (allowChat=false).";
                    }
                    String msg = SimpleTool.str(args, "message", "").trim();
                    if (msg.isEmpty()) return "ERROR: 'message' is required.";
                    return MinecraftThread.call(() -> {
                        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
                        if (h == null) return "ERROR: not connected to a world/server.";
                        h.sendChatMessage(msg);
                        return "Sent chat: " + msg;
                    });
                },
                true,
                args -> "send_chat: " + SimpleTool.str(args, "message", "?")));
    }

    private static String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
