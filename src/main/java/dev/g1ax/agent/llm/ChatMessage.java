package dev.g1ax.agent.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * A provider-neutral conversation message. Mirrors the OpenAI/Groq message shape closely
 * (which is the richer of the two formats); {@link GeminiProvider} translates it down to
 * Gemini's {@code contents[]} form.
 *
 * Roles used by the agent loop:
 *  - "system"    one leading system/instruction message
 *  - "user"      the player's message
 *  - "assistant" model output: either free text ({@link #content}) or {@link #toolCalls}
 *  - "tool"      the result of one tool call, tagged with {@link #toolCallId} and {@link #name}
 */
public class ChatMessage {
    public final String role;
    public final String content;
    public final List<ToolCall> toolCalls; // assistant messages that requested tools
    public final String toolCallId;        // for role=="tool": which call this answers
    public final String name;              // for role=="tool": the tool name

    private ChatMessage(String role, String content, List<ToolCall> toolCalls,
                        String toolCallId, String name) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.name = name;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null);
    }

    public static ChatMessage assistantText(String content) {
        return new ChatMessage("assistant", content, null, null, null);
    }

    public static ChatMessage assistantToolCalls(List<ToolCall> calls) {
        return new ChatMessage("assistant", null, new ArrayList<>(calls), null, null);
    }

    public static ChatMessage toolResult(String toolCallId, String name, String content) {
        return new ChatMessage("tool", content, null, toolCallId, name);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
