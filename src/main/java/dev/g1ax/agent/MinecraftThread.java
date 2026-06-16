package dev.g1ax.agent;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Marshals work onto Minecraft's client (render) thread and blocks for the result.
 *
 * The agent loop runs on a background thread (network I/O must never touch the render thread),
 * but almost everything the tools read or change — inventory, world, player, sending chat —
 * is only safe to access on the client thread. Every such tool wraps its body in
 * {@link #call(Supplier)} so the actual game access happens where the game expects it.
 */
public final class MinecraftThread {
    private static final long TIMEOUT_SECONDS = 5;

    private MinecraftThread() {}

    /**
     * Run {@code fn} on the client thread and return its value. If we're already on the client
     * thread the supplier runs inline (avoids a deadlock when a tool is somehow invoked there).
     * Propagates the supplier's exception so the tool/registry can report it.
     */
    public static <T> T call(Supplier<T> fn) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) {
            return fn.get();
        }
        CompletableFuture<T> future = client.submit(fn);
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause.getMessage(), cause);
        }
    }

    /** Fire-and-forget variant for actions whose return value we don't need. */
    public static void run(Runnable r) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) {
            r.run();
        } else {
            client.execute(r);
        }
    }
}
