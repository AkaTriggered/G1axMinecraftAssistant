package dev.g1ax.agent.llm;

import java.io.IOException;

/**
 * A non-2xx HTTP response from a provider. Carries the status code and (for 429s) the suggested
 * retry delay, plus a trimmed body so the agent can render a friendly message instead of a raw
 * JSON error dump.
 */
public class HttpException extends IOException {
    public final int statusCode;
    public final long retryAfterMs;
    public final String body;

    public HttpException(int statusCode, String body, long retryAfterMs) {
        super("HTTP " + statusCode + ": " + snippet(body));
        this.statusCode = statusCode;
        this.body = body;
        this.retryAfterMs = retryAfterMs;
    }

    public boolean isRateLimit() {
        return statusCode == 429;
    }

    public boolean isAuthError() {
        return statusCode == 401 || statusCode == 403;
    }

    private static String snippet(String body) {
        if (body == null) return "";
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
