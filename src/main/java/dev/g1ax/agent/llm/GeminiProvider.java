package dev.g1ax.agent.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini backend. Gemini's REST shape differs from OpenAI's in several ways the
 * translation here has to handle:
 *  - roles are only "user" and "model" (assistant -> "model"); there is no "tool"/"system" role
 *  - the system prompt goes in a top-level {@code systemInstruction}
 *  - tool calls are {@code functionCall} parts on a model turn; results are {@code functionResponse}
 *    parts sent back on a {@code user} turn
 *  - tools are declared under {@code tools[0].functionDeclarations}
 *  - JSON-Schema must not carry unsupported keywords; our schemas only use type/description/
 *    properties/required, which Gemini accepts.
 */
public class GeminiProvider implements LLMProvider {
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final Gson GSON = new Gson();

    private final String apiKey;
    private final String model;

    public GeminiProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public ChatResult chat(List<ChatMessage> messages, List<ToolSpec> tools, AgentSettings settings) throws Exception {
        JsonObject body = new JsonObject();

        if (settings.systemPrompt != null && !settings.systemPrompt.isEmpty()) {
            body.add("systemInstruction", textContent(null, settings.systemPrompt));
        }

        JsonArray contents = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject c = toGeminiContent(m);
            if (c != null) contents.add(c);
        }
        body.add("contents", contents);

        if (tools != null && !tools.isEmpty()) {
            JsonArray decls = new JsonArray();
            for (ToolSpec t : tools) {
                JsonObject d = new JsonObject();
                d.addProperty("name", t.name);
                d.addProperty("description", t.description);
                if (hasProperties(t.parameters)) {
                    d.add("parameters", t.parameters);
                }
                decls.add(d);
            }
            JsonObject toolObj = new JsonObject();
            toolObj.add("functionDeclarations", decls);
            JsonArray toolArr = new JsonArray();
            toolArr.add(toolObj);
            body.add("tools", toolArr);
        }

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", settings.temperature);
        if (settings.maxTokens > 0) genConfig.addProperty("maxOutputTokens", settings.maxTokens);
        body.add("generationConfig", genConfig);

        String url = String.format(ENDPOINT, model, apiKey);
        String response = Http.postJsonWithRetry(url, new HashMap<>(), GSON.toJson(body));

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        if (!json.has("candidates") || json.getAsJsonArray("candidates").isEmpty()) {
            // Could be a safety block or empty output; surface nothing rather than crashing.
            return new ChatResult(null, new ArrayList<>());
        }
        JsonObject content = json.getAsJsonArray("candidates").get(0)
                .getAsJsonObject().getAsJsonObject("content");

        List<ToolCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        if (content != null && content.has("parts")) {
            for (JsonElement el : content.getAsJsonArray("parts")) {
                JsonObject part = el.getAsJsonObject();
                if (part.has("functionCall")) {
                    JsonObject fc = part.getAsJsonObject("functionCall");
                    String fnName = fc.get("name").getAsString();
                    JsonObject args = fc.has("args") && fc.get("args").isJsonObject()
                            ? fc.getAsJsonObject("args") : new JsonObject();
                    calls.add(new ToolCall("gemini_" + fnName + "_" + calls.size(), fnName, args));
                } else if (part.has("text")) {
                    text.append(part.get("text").getAsString());
                }
            }
        }
        String finalText = text.length() > 0 ? text.toString() : null;
        return new ChatResult(finalText, calls);
    }

    /** Translate one neutral message into a Gemini {@code content} object (or null to skip). */
    private JsonObject toGeminiContent(ChatMessage m) {
        switch (m.role) {
            case "user":
                return textContent("user", m.content != null ? m.content : "");
            case "assistant":
                if (m.hasToolCalls()) {
                    JsonArray parts = new JsonArray();
                    for (ToolCall c : m.toolCalls) {
                        JsonObject fc = new JsonObject();
                        fc.addProperty("name", c.name);
                        fc.add("args", c.args);
                        JsonObject part = new JsonObject();
                        part.add("functionCall", fc);
                        parts.add(part);
                    }
                    return content("model", parts);
                }
                return textContent("model", m.content != null ? m.content : "");
            case "tool": {
                JsonObject fr = new JsonObject();
                fr.addProperty("name", m.name);
                JsonObject resp = new JsonObject();
                resp.addProperty("result", m.content != null ? m.content : "");
                fr.add("response", resp);
                JsonObject part = new JsonObject();
                part.add("functionResponse", fr);
                JsonArray parts = new JsonArray();
                parts.add(part);
                // Gemini expects function responses delivered on a "user" turn.
                return content("user", parts);
            }
            default:
                return null;
        }
    }

    private static JsonObject textContent(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        return content(role, parts);
    }

    private static JsonObject content(String role, JsonArray parts) {
        JsonObject c = new JsonObject();
        if (role != null) c.addProperty("role", role);
        c.add("parts", parts);
        return c;
    }

    private static boolean hasProperties(JsonObject schema) {
        return schema != null && schema.has("properties")
                && schema.getAsJsonObject("properties").size() > 0;
    }
}
