package dev.g1ax.agent.tool;

import com.google.gson.JsonObject;

import java.util.function.Function;

/**
 * Lightweight {@link Tool} built from a name, description, schema, and a lambda body — so the
 * concrete tool classes can register many tools without one class per tool. Used by the
 * {@code *Tools} factories.
 */
public class SimpleTool implements Tool {
    @FunctionalInterface
    public interface Body {
        String run(JsonObject args) throws Exception;
    }

    private final String name;
    private final String description;
    private final JsonObject schema;
    private final Body body;
    private final boolean action;
    private final Function<JsonObject, String> label;

    public SimpleTool(String name, String description, JsonObject schema, Body body) {
        this(name, description, schema, body, false, null);
    }

    public SimpleTool(String name, String description, JsonObject schema, Body body,
                      boolean action, Function<JsonObject, String> label) {
        this.name = name;
        this.description = description;
        this.schema = schema;
        this.body = body;
        this.action = action;
        this.label = label;
    }

    @Override public String name() { return name; }
    @Override public String description() { return description; }
    @Override public JsonObject parametersSchema() { return schema; }
    @Override public String execute(JsonObject args) throws Exception { return body.run(args); }
    @Override public boolean isAction() { return action; }

    @Override
    public String feedbackLabel(JsonObject args) {
        return label != null ? label.apply(args) : name;
    }

    // ---- small arg helpers shared by tool bodies ----

    public static String str(JsonObject args, String key, String def) {
        return args != null && args.has(key) && !args.get(key).isJsonNull()
                ? args.get(key).getAsString() : def;
    }

    public static int integer(JsonObject args, String key, int def) {
        try {
            return args != null && args.has(key) && !args.get(key).isJsonNull()
                    ? args.get(key).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }
}
