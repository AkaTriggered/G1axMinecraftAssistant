package dev.g1ax.agent;

import dev.g1ax.agent.llm.ChatMessage;
import dev.g1ax.agent.llm.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-conversation state: the running message history (so the agent is multi-turn) plus the
 * session trust flag that gates world-changing actions. The system prompt is NOT stored here —
 * it lives in {@link dev.g1ax.Config}/the provider settings and is supplied fresh each request.
 */
public class AgentSession {
    /** Max messages retained (excluding the system prompt). Older turns are trimmed. */
    private static final int MAX_HISTORY = 24;

    private final List<ChatMessage> history = new ArrayList<>();
    private boolean trusted;

    public AgentSession(boolean trustedByDefault) {
        this.trusted = trustedByDefault;
    }

    public List<ChatMessage> history() {
        return history;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    public void addUser(String content) {
        history.add(ChatMessage.user(content));
        trim();
    }

    public void addAssistantText(String content) {
        history.add(ChatMessage.assistantText(content));
        trim();
    }

    public void addAssistantToolCalls(List<ToolCall> calls) {
        history.add(ChatMessage.assistantToolCalls(calls));
        trim();
    }

    public void addToolResult(String toolCallId, String name, String content) {
        history.add(ChatMessage.toolResult(toolCallId, name, content));
        trim();
    }

    public void reset() {
        history.clear();
    }

    /**
     * Keep history bounded while preserving validity: trim oldest messages, then drop any leading
     * messages that aren't a "user" turn so providers never see a dangling tool/assistant-tool-call
     * at the start of the conversation.
     */
    private void trim() {
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
        while (!history.isEmpty() && !"user".equals(history.get(0).role)) {
            history.remove(0);
        }
    }
}
