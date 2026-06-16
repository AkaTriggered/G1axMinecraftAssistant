package dev.g1ax.agent.llm;

import com.google.gson.JsonObject;

/**
 * A single tool invocation requested by the model. Provider-neutral: Groq supplies the
 * {@code id} and arguments as a JSON string; for Gemini we synthesize an id and the args
 * arrive as a JSON object. Either way we normalize to a parsed {@link JsonObject}.
 */
public class ToolCall {
    public final String id;
    public final String name;
    public final JsonObject args;

    public ToolCall(String id, String name, JsonObject args) {
        this.id = id;
        this.name = name;
        this.args = args != null ? args : new JsonObject();
    }
}
