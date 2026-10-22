package com.newsbot;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes a carousel through the official Instagram Graph API (content publishing):
 * child containers -> carousel container -> media_publish.
 * Needs a professional (Business/Creator) account and an access token with publish permission.
 */
final class InstagramPublisher {
    private final String base;
    private final String userId;
    private final String token;

    InstagramPublisher(String host, String version, String userId, String token) {
        this.base = "https://" + host + "/" + version;
        this.userId = userId;
        this.token = token;
    }

    /** Returns the id of the published media. */
    String publishCarousel(List<String> imageUrls, String caption) throws Exception {
        List<String> children = new ArrayList<>();
        for (String url : imageUrls) {
            children.add(createContainer(Map.of("image_url", url, "is_carousel_item", "true")));
        }
        children.forEach(this::waitUntilFinished);

        String parent = createContainer(Map.of(
                "media_type", "CAROUSEL",
                "children", String.join(",", children),
                "caption", caption));
        waitUntilFinished(parent);

        // Deliberately not retried: a retry after a timeout could publish the post twice.
        JsonNode res = post("/" + userId + "/media_publish", Map.of("creation_id", parent));
        return res.path("id").asText();
    }

    private String createContainer(Map<String, String> params) throws Exception {
        JsonNode res = post("/" + userId + "/media", params);
        String id = res.path("id").asText();
        if (id.isBlank()) {
            throw new IllegalStateException("No container id in response: " + res);
        }
        return id;
    }

    private void waitUntilFinished(String containerId) {
        try {
            for (int i = 0; i < 30; i++) {
                String body = Http.send(Http.request(base + "/" + containerId + "?fields=status_code&access_token="
                        + Http.enc(token)).GET().build());
                String status = Http.JSON.readTree(body).path("status_code").asText();
                if ("FINISHED".equals(status)) {
                    return;
                }
                if ("ERROR".equals(status) || "EXPIRED".equals(status)) {
                    throw new IllegalStateException("Container " + containerId + " ended with status " + status);
                }
                Thread.sleep(10_000);
            }
            throw new IllegalStateException("Timed out waiting for container " + containerId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Status check failed for container " + containerId, e);
        }
    }

    private JsonNode post(String path, Map<String, String> params) throws Exception {
        Map<String, String> all = new LinkedHashMap<>(params);
        all.put("access_token", token);
        return Http.postForm(base + path, all);
    }
}
