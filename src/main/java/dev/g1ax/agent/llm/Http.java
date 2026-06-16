package dev.g1ax.agent.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal JSON-over-HTTP POST helper shared by the providers, with 429 rate-limit retry. */
final class Http {
    private static final int MAX_RETRIES = 2;
    private static final long MAX_BACKOFF_MS = 12_000;
    private static final Pattern RETRY_HINT = Pattern.compile("try again in ([0-9.]+)s");

    private Http() {}

    /**
     * POST JSON, retrying automatically on HTTP 429 using the server's suggested delay
     * (Retry-After header or the "try again in Xs" hint in the body). On other non-2xx codes,
     * or after retries are exhausted, throws {@link HttpException}.
     */
    static String postJsonWithRetry(String urlStr, Map<String, String> headers, String body) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return postJson(urlStr, headers, body);
            } catch (HttpException e) {
                last = e;
                if (e.statusCode == 429 && attempt < MAX_RETRIES) {
                    long wait = Math.min(e.retryAfterMs > 0 ? e.retryAfterMs : 3000, MAX_BACKOFF_MS) + 300;
                    System.err.println("[G1axAssistant] rate limited (429); retrying in " + wait + "ms");
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    continue;
                }
                throw e;
            }
        }
        throw last != null ? last : new IOException("request failed");
    }

    private static String postJson(String urlStr, Map<String, String> headers, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        for (Map.Entry<String, String> h : headers.entrySet()) {
            conn.setRequestProperty(h.getKey(), h.getValue());
        }
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = stream != null ? readAll(stream) : "";
        if (code < 200 || code >= 300) {
            throw new HttpException(code, response, retryAfterMs(conn, response));
        }
        return response;
    }

    /** Best-effort retry delay: prefer the Retry-After header, else the body hint, else 0. */
    private static long retryAfterMs(HttpURLConnection conn, String body) {
        String header = conn.getHeaderField("retry-after");
        if (header != null && !header.isBlank()) {
            try {
                return (long) (Double.parseDouble(header.trim()) * 1000);
            } catch (NumberFormatException ignored) { /* not numeric */ }
        }
        Matcher m = RETRY_HINT.matcher(body);
        if (m.find()) {
            try {
                return (long) (Double.parseDouble(m.group(1)) * 1000);
            } catch (NumberFormatException ignored) { /* ignore */ }
        }
        return 0;
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
