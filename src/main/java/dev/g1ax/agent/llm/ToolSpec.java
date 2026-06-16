package dev.g1ax.agent.llm;

import com.google.gson.JsonObject;

/**
 * Provider-neutral description of one callable tool: name, human description, and a JSON-Schema
 * object describing its parameters. Both providers translate this into their own function-spec
 * shape ({@code function.parameters} for Groq, {@code functionDeclarations[].parameters} for
 * Gemini).
 */
public class ToolSpec {
    public final String name;
    public final String description;
    public final JsonObject parameters; // JSON Schema {type:object, properties:{...}, required:[...]}

    public ToolSpec(String name, String description, JsonObject parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }
}
