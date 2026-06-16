package dev.g1ax.agent.llm;

/**
 * Immutable snapshot of the model knobs a provider needs for one request. Built from
 * {@link dev.g1ax.Config} so the provider layer never touches the config type directly.
 */
public class AgentSettings {
    public final String systemPrompt;
    public final double temperature;
    public final int maxTokens;

    public AgentSettings(String systemPrompt, double temperature, int maxTokens) {
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }
}
