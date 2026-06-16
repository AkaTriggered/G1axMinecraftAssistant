package dev.g1ax.agent;

/**
 * Receives live progress events from the agent loop so the UI can show the agent "thinking" and
 * which tools it is calling. Implementations should be cheap and non-blocking (the client wires
 * this to a marshaled chat print).
 */
@FunctionalInterface
public interface ProgressListener {
    /** Called with a short human label for each step, e.g. {@code "lookup_recipe: hopper"}. */
    void onStep(String label);

    /** A listener that ignores everything. */
    ProgressListener NOOP = label -> {};
}
