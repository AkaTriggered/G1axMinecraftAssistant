package dev.g1ax.agent.llm;

import java.util.Collections;
import java.util.List;

/**
 * One turn of model output. A model may emit free text, a set of tool calls, or both in the
 * same turn. The agent loop continues whenever {@link #hasToolCalls()} is true and otherwise
 * treats {@link #text} as the final answer.
 */
public class ChatResult {
    public final String text;             // may be null/empty when the model only called tools
    public final List<ToolCall> toolCalls; // never null; empty when the model gave a final answer

    public ChatResult(String text, List<ToolCall> toolCalls) {
        this.text = text;
        this.toolCalls = toolCalls != null ? toolCalls : Collections.emptyList();
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
