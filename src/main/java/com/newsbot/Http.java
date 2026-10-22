package com.newsbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Small HTTP helper shared by the fetcher, image host and Instagram client. */
final class Http {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private Http() {}

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static String form(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /** Sends the request and returns the body; throws with the body included on non-2xx. */
    static String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> res = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + res.statusCode() + " from " + request.uri().getHost() + ": " + res.body());
        }
        return res.body();
    }

    /** Like {@link #send} but retries with backoff. Only use for idempotent calls. */
    static String sendWithRetry(HttpRequest request, int attempts) throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return send(request);
            } catch (IOException e) {
                last = e;
                if (i < attempts) {
                    Thread.sleep(2000L * i);
                }
            }
        }
        throw last;
    }

    static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "Mozilla/5.0 (compatible; InstaNewsBot/1.0)");
    }

    static JsonNode postForm(String url, Map<String, String> params) throws IOException, InterruptedException {
        HttpRequest req = request(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)))
                .build();
        return JSON.readTree(send(req));
    }
}
