package dev.g1ax.agent.tool;

import com.google.gson.JsonObject;
import dev.g1ax.agent.llm.ToolSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the agent's tools, exposes them to providers as {@link ToolSpec}s, and dispatches calls
 * by name. Dispatch never throws: unknown tools and tool exceptions are turned into an error
 * string so the model can read the failure and recover on the next loop iteration.
 */
public class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public ToolRegistry registerAll(Collection<? extends Tool> toRegister) {
        for (Tool t : toRegister) register(t);
        return this;
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    /** Provider-neutral specs for every registered tool, in registration order. */
    public List<ToolSpec> specs() {
        List<ToolSpec> specs = new ArrayList<>(tools.size());
        for (Tool t : tools.values()) {
            specs.add(new ToolSpec(t.name(), t.description(), t.parametersSchema()));
        }
        return specs;
    }

    /**
     * Execute a tool by name with parsed args. Returns the tool's string result, or an
     * {@code ERROR: ...} string the model can react to (never propagates the exception).
     */
    public String dispatch(String name, JsonObject args) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return "ERROR: unknown tool '" + name + "'. Use one of the provided tools.";
        }
        try {
            String result = tool.execute(args != null ? args : new JsonObject());
            return result != null ? result : "(no output)";
        } catch (Exception e) {
            String msg = e.getMessage();
            return "ERROR running " + name + ": " + (msg != null ? msg : e.getClass().getSimpleName());
        }
    }
}
