package dev.g1ax.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tiny fluent builder for the JSON-Schema {@code {type:object, properties, required}} blocks
 * that tool parameter specs need. Keeps tool definitions readable instead of hand-assembling
 * nested {@link JsonObject}s.
 *
 * Example:
 * <pre>{@code
 * SchemaBuilder.object()
 *     .stringProp("item", "The item id or name, e.g. 'hopper' or 'minecraft:hopper'", true)
 *     .intProp("radius", "Search radius in blocks (default 16)", false)
 *     .build();
 * }</pre>
 */
public class SchemaBuilder {
    private final JsonObject schema = new JsonObject();
    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    private SchemaBuilder() {
        schema.addProperty("type", "object");
    }

    public static SchemaBuilder object() {
        return new SchemaBuilder();
    }

    /** An empty object schema, for tools that take no arguments. */
    public static JsonObject empty() {
        return object().build();
    }

    public SchemaBuilder stringProp(String name, String description, boolean req) {
        return prop(name, "string", description, req);
    }

    public SchemaBuilder intProp(String name, String description, boolean req) {
        return prop(name, "integer", description, req);
    }

    public SchemaBuilder numberProp(String name, String description, boolean req) {
        return prop(name, "number", description, req);
    }

    public SchemaBuilder boolProp(String name, String description, boolean req) {
        return prop(name, "boolean", description, req);
    }

    private SchemaBuilder prop(String name, String type, String description, boolean req) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", description);
        properties.add(name, p);
        if (req) required.add(name);
        return this;
    }

    public JsonObject build() {
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }
}
