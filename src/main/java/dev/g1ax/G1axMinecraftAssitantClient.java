package dev.g1ax;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.g1ax.agent.ProgressListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class G1axMinecraftAssitantClient implements ClientModInitializer {
    private static Config config;
    private static AIClient aiClient;
    private static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        try {
            config = Config.load();
            aiClient = new AIClient(config);

            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                dispatcher.register(literal("mcai")
                    .then(literal("config").executes(ctx -> {
                        try {
                            MinecraftClient.getInstance().execute(() ->
                                MinecraftClient.getInstance().setScreen(new ConfigScreen(null, config)));
                            return 1;
                        } catch (Exception e) {
                            ctx.getSource().sendError(Text.literal("§c[G1axAssistant] Failed to open config: " + e.getMessage()));
                            return 0;
                        }
                    }))
                    .then(literal("toggle").executes(ctx -> {
                        enabled = !enabled;
                        ctx.getSource().sendFeedback(Text.literal(enabled ?
                            "§a[G1axAssistant] Enabled" : "§c[G1axAssistant] Disabled"));
                        return 1;
                    }))
                    .then(literal("reset").executes(ctx -> {
                        aiClient.resetSession();
                        ctx.getSource().sendFeedback(Text.literal("§d[G1axAssistant] §7Conversation memory cleared."));
                        return 1;
                    }))
                    .then(literal("trust")
                        .then(literal("on").executes(ctx -> {
                            aiClient.setTrusted(true);
                            ctx.getSource().sendFeedback(Text.literal("§c[G1axAssistant] Trust ON §7— the agent can now run commands, send chat, and edit configs."));
                            return 1;
                        }))
                        .then(literal("off").executes(ctx -> {
                            aiClient.setTrusted(false);
                            ctx.getSource().sendFeedback(Text.literal("§a[G1axAssistant] Trust OFF §7— actions are previewed, not executed (safe mode)."));
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal("§d[G1axAssistant] §7Trust is "
                                + (aiClient.isTrusted() ? "§cON" : "§aOFF") + "§7. Use /mcai trust on|off"));
                            return 1;
                        }))
                    .then(literal("key")
                        .then(argument("provider", StringArgumentType.word())
                            .then(argument("apikey", StringArgumentType.greedyString()).executes(ctx -> {
                                String prov = StringArgumentType.getString(ctx, "provider").toLowerCase();
                                String key = StringArgumentType.getString(ctx, "apikey").trim();
                                boolean ok = true;
                                switch (prov) {
                                    case "groq" -> config.groqApiKey = key;
                                    case "gemini" -> config.geminiApiKey = key;
                                    default -> ok = false;
                                }
                                if (!ok) {
                                    ctx.getSource().sendError(Text.literal("§c[G1axAssistant] Unknown provider '" + prov + "'. Use: groq or gemini"));
                                    return 0;
                                }
                                config.normalize();
                                config.save();
                                ctx.getSource().sendFeedback(Text.literal("§a[G1axAssistant] Saved " + prov
                                    + " key (" + maskKey(key) + ") → config/g1axminecraftassistant.json"));
                                return 1;
                            })))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal("§d[G1axAssistant] §7Usage: /mcai key <groq|gemini> <your-api-key>"));
                            return 1;
                        }))
                    .then(literal("provider")
                        .then(argument("which", StringArgumentType.word()).executes(ctx -> {
                            String which = StringArgumentType.getString(ctx, "which").toLowerCase();
                            if (!which.equals("auto") && !which.equals("groq") && !which.equals("gemini")) {
                                ctx.getSource().sendError(Text.literal("§c[G1axAssistant] Provider must be: auto, groq, or gemini"));
                                return 0;
                            }
                            config.provider = which;
                            config.save();
                            ctx.getSource().sendFeedback(Text.literal("§a[G1axAssistant] Provider set to §f" + which));
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal("§d[G1axAssistant] §7Provider is §f" + config.provider
                                + "§7. Set with /mcai provider <auto|groq|gemini>"));
                            return 1;
                        }))
                    .then(literal("status").executes(ctx -> {
                        var s = ctx.getSource();
                        s.sendFeedback(Text.literal("§d[G1axAssistant] §7Status"));
                        s.sendFeedback(Text.literal("§7 Provider: §f" + config.provider));
                        s.sendFeedback(Text.literal("§7 Groq key: " + (isSet(config.groqApiKey) ? "§aset" : "§cmissing")
                            + " §8" + config.groqModel));
                        s.sendFeedback(Text.literal("§7 Gemini key: " + (isSet(config.geminiApiKey) ? "§aset" : "§cmissing")
                            + " §8" + config.geminiModel));
                        s.sendFeedback(Text.literal("§7 Trust: " + (aiClient.isTrusted() ? "§cON" : "§aOFF (safe)")));
                        s.sendFeedback(Text.literal("§7 Get free keys: §econsole.groq.com §7/ §eaistudio.google.com/app/apikey"));
                        return 1;
                    }))
                    .then(literal("about").executes(ctx -> {
                        ctx.getSource().sendFeedback(Text.literal("§b[G1axAssistant] §7Agentic AI assistant by G1ax"));
                        ctx.getSource().sendFeedback(Text.literal("§7GitHub: §egithub.com/@AkaTriggered"));
                        ctx.getSource().sendFeedback(Text.literal("§7DM §eg1.ax §7for custom mods (paid only)"));
                        ctx.getSource().sendFeedback(Text.literal("§7Tool-using agent: inventory, real recipes, configs, commands"));
                        return 1;
                    }))
                    .then(literal("analyze").executes(ctx -> {
                        processMessage("Analyze my current performance and settings, and suggest improvements.");
                        return 1;
                    }))
                    .then(literal("optimize").then(argument("level", StringArgumentType.word()).executes(ctx -> {
                        String level = StringArgumentType.getString(ctx, "level");
                        processMessage("Optimize my game configs for " + level + " performance. "
                            + "Read the relevant performance-mod configs and apply sensible changes.");
                        return 1;
                    })))
                    .then(literal("list").then(literal("mods").executes(ctx -> {
                        processMessage("List my installed mods.");
                        return 1;
                    })))
                    .then(literal("list").then(literal("configs").executes(ctx -> {
                        processMessage("List my config files.");
                        return 1;
                    })))
                    .executes(ctx -> {
                        sendUsage(ctx.getSource());
                        return 1;
                    })
                    .then(argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                        if (!enabled) {
                            ctx.getSource().sendError(Text.literal("§c[G1axAssistant] Disabled. Use /mcai toggle"));
                            return 0;
                        }
                        processMessage(StringArgumentType.getString(ctx, "message"));
                        return 1;
                    })));
            });

            ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
                try {
                    if (!enabled) return;

                    String text = message.getString();
                    MinecraftClient client = MinecraftClient.getInstance();

                    if (client.player != null && text.contains(">")) {
                        String[] parts = text.split(">", 2);
                        if (parts.length == 2) {
                            String playerMsg = parts[1].trim();
                            // Only react to the local player's own "@ai ..." messages (asking the
                            // same question twice is allowed; we just avoid replying to our own output).
                            String selfName = client.player.getGameProfile().getName();
                            boolean fromSelf = parts[0].contains(selfName);
                            if (fromSelf && playerMsg.toLowerCase().startsWith("@ai ")) {
                                String query = playerMsg.substring(4).trim();
                                if (!query.isEmpty()) {
                                    processMessage(query);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing chat: " + e.getMessage());
                }
            });

            System.out.println("[G1axAssistant] Agent initialized. Type @ai <message> in chat or use /mcai <message>");
            System.out.println("[G1axAssistant] Created by G1ax | github.com/@AkaTriggered | DM g1.ax for custom mods");
        } catch (Exception e) {
            System.err.println("[G1axAssistant] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendUsage(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src) {
        src.sendFeedback(Text.literal("§d[G1axAssistant] §7Agentic assistant — usage:"));
        src.sendFeedback(Text.literal("§7  /mcai <message> §8- ask anything; the agent observes & acts"));
        src.sendFeedback(Text.literal("§7  @ai <message> §8- ask in chat"));
        src.sendFeedback(Text.literal("§e  /mcai key <groq|gemini> <key> §8- set an API key (required)"));
        src.sendFeedback(Text.literal("§7  /mcai provider <auto|groq|gemini> §8- pick provider"));
        src.sendFeedback(Text.literal("§7  /mcai status §8- show keys/provider/trust"));
        src.sendFeedback(Text.literal("§7  /mcai trust on|off §8- allow/preview in-game actions"));
        src.sendFeedback(Text.literal("§7  /mcai analyze §8| optimize <low|medium|high> §8| reset"));
        src.sendFeedback(Text.literal("§7  /mcai config §8- open the settings screen"));
    }

    private static boolean isSet(String key) {
        return key != null && !key.isEmpty();
    }

    /** Mask an API key for display, e.g. "gsk_…a1b2". */
    private static String maskKey(String key) {
        if (key == null || key.isEmpty()) return "empty";
        if (key.length() <= 6) return "••••";
        return key.substring(0, 3) + "…" + key.substring(key.length() - 4);
    }

    private void processMessage(String message) {
        new Thread(() -> {
            try {
                ProgressListener progress = label -> showLine("§8» §7" + truncate(label, 90));
                String response = aiClient.sendMessage(message, progress);
                showLine("§d[G1axAssistant] §f" + response);
            } catch (Exception e) {
                showLine("§c[G1axAssistant] Error: " + e.getMessage());
            }
        }, "G1axAssistant-Agent").start();
    }

    /** Print a line to the player's chat, marshaled onto the client thread. */
    private static void showLine(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(text), false);
            }
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
