package dev.g1ax.agent;

import com.google.gson.Gson;
import dev.g1ax.agent.llm.AgentSettings;
import dev.g1ax.agent.llm.ChatResult;
import dev.g1ax.agent.llm.HttpException;
import dev.g1ax.agent.llm.LLMProvider;
import dev.g1ax.agent.llm.ToolCall;
import dev.g1ax.agent.llm.ToolSpec;
import dev.g1ax.agent.tool.Tool;
import dev.g1ax.agent.tool.ToolRegistry;

import java.util.List;

/**
 * The agent brain: a multi-step tool-calling (ReAct) loop. Given a user message it repeatedly
 * asks the model what to do, executes any tools the model requests, feeds the results back, and
 * loops until the model produces a final text answer or the step budget is exhausted.
 *
 * Runs on a background thread (network I/O). Tools that touch the game marshal themselves onto the
 * client thread. Providers are tried in order, giving Groq->Gemini fallback for free.
 */
public class Agent {
    private static final Gson GSON = new Gson();
    /** Cap on a single tool result fed back to the model, to bound token growth across the loop. */
    private static final int MAX_TOOL_RESULT_CHARS = 3000;

    private final List<LLMProvider> providers;
    private final ToolRegistry registry;
    private final String systemPrompt;
    private final double temperature;
    private final int maxTokens;
    private final int maxIterations;

    public Agent(List<LLMProvider> providers, ToolRegistry registry, String systemPrompt,
                 double temperature, int maxTokens, int maxIterations) {
        this.providers = providers;
        this.registry = registry;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxIterations = Math.max(1, maxIterations);
    }

    /** True if at least one provider has credentials. Used to give a clean "configure keys" error. */
    public boolean hasConfiguredProvider() {
        for (LLMProvider p : providers) {
            if (p.isConfigured()) return true;
        }
        return false;
    }

    /**
     * Run the loop for one user message and return the final answer text. Appends the whole
     * exchange (user, assistant, tool turns) to the session so the conversation is multi-turn.
     */
    public String run(AgentSession session, String userMessage, ProgressListener progress) {
        if (!hasConfiguredProvider()) {
            return "No API key set. Run: /mcai key groq <your-key>  (free at console.groq.com), "
                    + "or /mcai key gemini <your-key>. Check with /mcai status.";
        }
        ProgressListener pl = progress != null ? progress : ProgressListener.NOOP;
        AgentSettings settings = new AgentSettings(systemPrompt, temperature, maxTokens);
        List<ToolSpec> specs = registry.specs();

        session.addUser(userMessage);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            ChatResult result;
            try {
                result = callWithFallback(session, specs, settings);
            } catch (Exception e) {
                return friendlyError(e);
            }

            if (!result.hasToolCalls()) {
                String text = result.text != null && !result.text.isBlank()
                        ? result.text : "(the assistant returned no answer)";
                session.addAssistantText(text);
                return text;
            }

            // The model wants to use tools. Record the request, run them, feed results back.
            if (result.text != null && !result.text.isBlank()) {
                pl.onStep(result.text.strip());
            }
            session.addAssistantToolCalls(result.toolCalls);

            for (ToolCall call : result.toolCalls) {
                Tool tool = registry.get(call.name);
                String label = tool != null ? tool.feedbackLabel(call.args) : call.name;
                pl.onStep(label);

                String toolResult;
                if (tool != null && tool.isAction() && !session.isTrusted()) {
                    toolResult = blockedBySafeMode(call);
                } else {
                    toolResult = registry.dispatch(call.name, call.args);
                }
                session.addToolResult(call.id, call.name, truncate(toolResult));
            }
        }

        // Step budget exhausted — ask for the best summary we have rather than looping forever.
        return "I ran out of steps before finishing. Try narrowing the request, or ask me to continue.";
    }

    /** Try each configured provider in order; return the first success, else throw the last error. */
    private ChatResult callWithFallback(AgentSession session, List<ToolSpec> specs, AgentSettings settings)
            throws Exception {
        Exception last = null;
        for (LLMProvider provider : providers) {
            if (!provider.isConfigured()) continue;
            try {
                return provider.chat(session.history(), specs, settings);
            } catch (Exception e) {
                last = e;
                System.err.println("[G1axAssistant] provider " + provider.name() + " failed: " + e.getMessage());
            }
        }
        throw last != null ? last : new IllegalStateException("no configured providers");
    }

    private String blockedBySafeMode(ToolCall call) {
        return "BLOCKED by safe mode: the action '" + call.name + "' was NOT executed. "
                + "Arguments: " + GSON.toJson(call.args) + ". "
                + "Tell the user the exact action you wanted to take and that they can allow actions "
                + "with '/mcai trust on', then stop calling this tool.";
    }

    /** Turn a raw provider failure into a short, actionable chat message. */
    private String friendlyError(Exception e) {
        Throwable cause = e;
        while (cause != null && !(cause instanceof HttpException)) {
            cause = cause.getCause();
        }
        if (cause instanceof HttpException he) {
            if (he.isRateLimit()) {
                long secs = Math.max(1, he.retryAfterMs / 1000);
                return "§eRate limit hit (provider free tier). §7Wait ~" + secs + "s and retry. "
                        + "Tips: §f/mcai reset§7 trims history to save tokens, or add a fallback key with "
                        + "§f/mcai key gemini <key>§7.";
            }
            if (he.isAuthError()) {
                return "§cAPI key rejected (HTTP " + he.statusCode + "). §7Re-check with /mcai status and "
                        + "re-set it: /mcai key <groq|gemini> <key>.";
            }
            return "§cProvider error HTTP " + he.statusCode + ". §7Try again, or switch with "
                    + "/mcai provider <auto|groq|gemini>.";
        }
        return "§cAI request failed: §7" + e.getMessage();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_TOOL_RESULT_CHARS) return s;
        return s.substring(0, MAX_TOOL_RESULT_CHARS)
                + "\n…[truncated " + (s.length() - MAX_TOOL_RESULT_CHARS) + " chars]";
    }
}
