package dev.g1ax;

import dev.g1ax.agent.Agent;
import dev.g1ax.agent.AgentSession;
import dev.g1ax.agent.ProgressListener;
import dev.g1ax.agent.llm.GeminiProvider;
import dev.g1ax.agent.llm.GroqProvider;
import dev.g1ax.agent.llm.LLMProvider;
import dev.g1ax.agent.tool.ActionTools;
import dev.g1ax.agent.tool.GameStateTools;
import dev.g1ax.agent.tool.RecipeTools;
import dev.g1ax.agent.tool.SystemTools;
import dev.g1ax.agent.tool.ToolRegistry;

import java.util.List;

/**
 * Facade between the mod's command/chat layer and the agent brain. Builds the tool registry once,
 * owns the conversation {@link AgentSession}, and constructs the provider list + {@link Agent}
 * from the live {@link Config} on each call (so API-key/model/safety changes from the GUI take
 * effect immediately, without a game restart).
 */
public class AIClient {
    private static final String SYSTEM_PROMPT = """
You are G1axAssistant, an autonomous in-game agent inside a Minecraft Fabric 1.21 client mod by G1ax.

You are a TOOL-USING AGENT: think, call tools to observe or act, read the results, and repeat until
the task is done — then give a short final answer.

PRINCIPLES
- GROUND EVERYTHING IN TOOLS. Never guess the player's inventory, position, world state, recipes,
  configs, mods, or performance. Call the relevant tool. For crafting, ALWAYS use lookup_recipe /
  check_craftable — the recipe data is real; your memory is not.
- Be efficient: call only the tools you need, then stop and answer. Don't re-call a tool you already
  have the answer from.
- ACTIONS (run_command, send_chat, write_config) change the world. In safe mode they are blocked and
  you are told so — when that happens, tell the user the EXACT action you intended and that they can
  allow it with '/mcai trust on'. Never repeatedly retry a blocked action.
- Only take actions the user actually asked for, and prefer the least invasive option.

STYLE
- Final answers are shown in Minecraft chat: keep them to a few short lines, concrete and specific.
- Cite the real numbers you observed (e.g. "you have 2/5 iron ingots"). Use • for bullet lists.
- If a recipe isn't in the registry, say so and advise from general knowledge.

Created by G1ax | github.com/@AkaTriggered | DM g1.ax for custom mods (paid only).
""";

    private final Config config;
    private final ToolRegistry registry;
    private final AgentSession session;

    public AIClient(Config config) {
        this.config = config;
        this.registry = new ToolRegistry();
        GameStateTools.register(registry);
        RecipeTools.register(registry);
        SystemTools.register(registry);
        ActionTools.register(registry, () -> config.allowCommands, () -> config.allowChat);
        this.session = new AgentSession(config.trustActions);
    }

    /** Backward-compatible entry point (no live progress). */
    public String sendMessage(String userMessage) {
        return sendMessage(userMessage, ProgressListener.NOOP);
    }

    /** Run the agent loop for one user message, streaming step feedback through {@code progress}. */
    public String sendMessage(String userMessage, ProgressListener progress) {
        Agent agent = new Agent(buildProviders(), registry, SYSTEM_PROMPT,
                config.temperature, config.maxTokens, config.maxIterations);
        return agent.run(session, userMessage, progress);
    }

    public void resetSession() {
        session.reset();
    }

    public boolean isTrusted() {
        return session.isTrusted();
    }

    public void setTrusted(boolean trusted) {
        session.setTrusted(trusted);
    }

    /** Whether any provider currently has an API key configured. */
    public boolean hasApiKey() {
        return (config.groqApiKey != null && !config.groqApiKey.isEmpty())
                || (config.geminiApiKey != null && !config.geminiApiKey.isEmpty());
    }

    private List<LLMProvider> buildProviders() {
        GroqProvider groq = new GroqProvider(config.groqApiKey, config.groqModel);
        GeminiProvider gemini = new GeminiProvider(config.geminiApiKey, config.geminiModel);
        String pref = config.provider == null ? "auto" : config.provider.toLowerCase();
        return switch (pref) {
            case "groq" -> List.of(groq);
            case "gemini" -> List.of(gemini);
            default -> List.of(groq, gemini);
        };
    }
}
