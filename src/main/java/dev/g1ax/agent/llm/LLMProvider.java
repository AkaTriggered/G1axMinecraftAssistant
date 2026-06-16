package dev.g1ax.agent.llm;

import dev.g1ax.agent.llm.ToolSpec;

import java.util.List;

/**
 * A chat-completions backend that supports native tool/function calling. The agent loop is
 * written entirely against this interface, so providers (Groq, Gemini, ...) are interchangeable
 * and can be tried in fallback order.
 *
 * The {@code messages} list contains only the running conversation (user / assistant / tool
 * roles). The system prompt lives in {@link AgentSettings} so each provider can place it where
 * it belongs (a leading system message for Groq, {@code systemInstruction} for Gemini).
 */
public interface LLMProvider {
    /** Short id used in logs and progress feedback, e.g. "groq" or "gemini". */
    String name();

    /** True when this provider has the credentials it needs to be called. */
    boolean isConfigured();

    /**
     * Run one completion. Returns the model's text and/or the tool calls it wants executed.
     * Throws on transport/HTTP/parse failure so the caller can fall back to another provider.
     */
    ChatResult chat(List<ChatMessage> messages, List<ToolSpec> tools, AgentSettings settings) throws Exception;
}
