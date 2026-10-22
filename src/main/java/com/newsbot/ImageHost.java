package com.newsbot;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Instagram's API downloads images from a public HTTPS URL, so each slide is uploaded to imgbb first.
 * Images expire after 24 h. Any host that gives a public JPEG URL (S3, Cloudinary, ...) can replace this class.
 */
final class ImageHost {
    private final String apiKey;

    ImageHost(String apiKey) {
        this.apiKey = apiKey;
    }

    String upload(Path jpg) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(jpg));
        String url = "https://api.imgbb.com/1/upload?key=" + Http.enc(apiKey) + "&expiration=86400";
        HttpRequest req = Http.request(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(Http.form(Map.of("image", base64))))
                .build();
        JsonNode json = Http.JSON.readTree(Http.sendWithRetry(req, 3));
        JsonNode link = json.path("data").path("url");
        if (link.isMissingNode()) {
            throw new IllegalStateException("Image host returned no URL: " + json);
        }
        return link.asText();
    }
}
