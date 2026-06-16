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
 * Groq backend, which speaks the OpenAI chat-completions + tool-calling dialect. This is the
 * richest of the two formats, so the neutral {@link ChatMessage} maps almost 1:1.
 */
public class GroqProvider implements LLMProvider {
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final Gson GSON = new Gson();

    private final String apiKey;
    private final String model;

    public GroqProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String name() {
        return "groq";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public ChatResult chat(List<ChatMessage> messages, List<ToolSpec> tools, AgentSettings settings) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", settings.temperature);
        if (settings.maxTokens > 0) body.addProperty("max_tokens", settings.maxTokens);

        JsonArray msgs = new JsonArray();
        if (settings.systemPrompt != null && !settings.systemPrompt.isEmpty()) {
            msgs.add(textMessage("system", settings.systemPrompt));
        }
        for (ChatMessage m : messages) {
            msgs.add(toGroqMessage(m));
        }
        body.add("messages", msgs);

        if (tools != null && !tools.isEmpty()) {
            JsonArray toolArr = new JsonArray();
            for (ToolSpec t : tools) {
                JsonObject fn = new JsonObject();
                fn.addProperty("name", t.name);
                fn.addProperty("description", t.description);
                fn.add("parameters", t.parameters);
                JsonObject wrapper = new JsonObject();
                wrapper.addProperty("type", "function");
                wrapper.add("function", fn);
                toolArr.add(wrapper);
            }
            body.add("tools", toolArr);
            body.addProperty("tool_choice", "auto");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        String response = Http.postJsonWithRetry(ENDPOINT, headers, GSON.toJson(body));

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");

        List<ToolCall> calls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement el : message.getAsJsonArray("tool_calls")) {
                JsonObject tc = el.getAsJsonObject();
                String id = tc.has("id") ? tc.get("id").getAsString() : "call_" + calls.size();
                JsonObject fn = tc.getAsJsonObject("function");
                String fnName = fn.get("name").getAsString();
                JsonObject args = parseArgs(fn.has("arguments") ? fn.get("arguments").getAsString() : "");
                calls.add(new ToolCall(id, fnName, args));
            }
        }
        String text = message.has("content") && !message.get("content").isJsonNull()
                ? message.get("content").getAsString() : null;
        return new ChatResult(text, calls);
    }

    private JsonObject toGroqMessage(ChatMessage m) {
        if ("tool".equals(m.role)) {
            JsonObject o = new JsonObject();
            o.addProperty("role", "tool");
            o.addProperty("tool_call_id", m.toolCallId);
            o.addProperty("name", m.name);
            o.addProperty("content", m.content != null ? m.content : "");
            return o;
        }
        if ("assistant".equals(m.role) && m.hasToolCalls()) {
            JsonObject o = new JsonObject();
            o.addProperty("role", "assistant");
            // content must be present (null is acceptable per OpenAI spec for tool-call turns)
            o.add("content", m.content != null ? new com.google.gson.JsonPrimitive(m.content)
                    : com.google.gson.JsonNull.INSTANCE);
            JsonArray arr = new JsonArray();
            for (ToolCall c : m.toolCalls) {
                JsonObject fn = new JsonObject();
                fn.addProperty("name", c.name);
                fn.addProperty("arguments", GSON.toJson(c.args));
                JsonObject tc = new JsonObject();
                tc.addProperty("id", c.id);
                tc.addProperty("type", "function");
                tc.add("function", fn);
                arr.add(tc);
            }
            o.add("tool_calls", arr);
            return o;
        }
        return textMessage(m.role, m.content != null ? m.content : "");
    }

    private static JsonObject textMessage(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static JsonObject parseArgs(String raw) {
        if (raw == null || raw.isBlank()) return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }
}
