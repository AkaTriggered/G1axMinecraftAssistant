package dev.g1ax.agent.tool;

import com.google.gson.JsonObject;

/**
 * One capability the agent can invoke. Implementations should be small and single-purpose;
 * the model decides when to call them. {@link #execute} returns a plain string that is fed
 * straight back to the model as the tool result, so make it compact and information-dense.
 */
public interface Tool {
    /** Stable snake_case identifier the model uses to call this tool. */
    String name();

    /** What the tool does and when to use it — the model reads this to decide. */
    String description();

    /** JSON-Schema object for the arguments (see {@link SchemaBuilder}). */
    JsonObject parametersSchema();

    /** Run the tool. May throw; the registry converts exceptions into an error result. */
    String execute(JsonObject args) throws Exception;

    /**
     * True for tools that change the world (run commands, send chat, write configs). The agent
     * surfaces these distinctly and they honor the safe-mode guard.
     */
    default boolean isAction() {
        return false;
    }

    /** Short human label for live progress feedback, e.g. {@code "lookup_recipe: hopper"}. */
    default String feedbackLabel(JsonObject args) {
        return name();
    }
}
