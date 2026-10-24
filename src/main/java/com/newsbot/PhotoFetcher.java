package com.newsbot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a photo for each story and returns it as a data: URI, so the slide HTML is self-contained
 * (no hot-link blocking, no slow remote loads while Selenium takes the screenshot).
 * Order tried: the image from the RSS item, then the article page's og:image.
 * Any failure just means that slide is rendered without a photo.
 */
final class PhotoFetcher {
    private static final int MIN_WIDTH = 400;       // below this the photo looks blurry on a 1080px slide
    private static final int GOOD_WIDTH = 700;      // stop looking once we have a photo this wide
    private static final int MAX_BYTES = 6_000_000;

    private static final Pattern OG_1 = Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)(?::secure_url)?[\"'][^>]*content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_2 = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image|twitter:image)(?::secure_url)?[\"']",
            Pattern.CASE_INSENSITIVE);

    private PhotoFetcher() {}

    /** Returns a data URI for the story's photo, or null when none could be found. */
    static String photoFor(NewsItem item) {
        List<String> candidates = new ArrayList<>();
        if (!item.imageUrl().isBlank()) {
            candidates.add(item.imageUrl());
        }

        Photo best = null;
        for (String url : candidates) {
            best = better(best, load(url));
        }
        if (best == null || best.width < GOOD_WIDTH) {
            String og = ogImage(item.link());
            if (og != null && !candidates.contains(og)) {
                best = better(best, load(og));
            }
        }
        return best == null ? null : best.dataUri;
    }

    private record Photo(String dataUri, int width) {}

    private static Photo better(Photo a, Photo b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.width > a.width ? b : a;
    }

    private static Photo load(String url) {
        try {
            byte[] bytes;
            String type;
            if (url.startsWith("file:")) {                       // lets you test with local files
                Path p = Path.of(URI.create(url));
                bytes = Files.readAllBytes(p);
                type = guessType(url, bytes);
            } else {
                bytes = Http.getBytes(url, Duration.ofSeconds(20));
                type = bytes == null ? null : guessType(url, bytes);
            }
            if (bytes == null || bytes.length < 2_000 || bytes.length > MAX_BYTES || type == null) {
                return null;
            }
            int width = -1;                                       // -1 = could not decode (e.g. WebP): trust Chrome
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                width = img.getWidth();
                if (width < MIN_WIDTH || img.getHeight() < 200) {
                    return null;
                }
            }
            String uri = "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
            return new Photo(uri, width < 0 ? GOOD_WIDTH : width);
        } catch (Exception e) {
            System.err.println("WARN: photo skipped (" + shorten(url) + "): " + e.getMessage());
            return null;
        }
    }

    /** Works out the image type from the file's first bytes (headers from news sites are often wrong). */
    private static String guessType(String url, byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length > 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b.length > 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return "image/gif";
        return null;   // HTML error pages, SVG logos, anything else
    }

    /** Reads og:image from the article page; null when the page has none or can't be fetched. */
    static String ogImage(String pageUrl) {
        if (pageUrl == null || !pageUrl.startsWith("http")) {
            return null;
        }
        try {
            HttpRequest req = Http.request(pageUrl).timeout(Duration.ofSeconds(20))
                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .setHeader("Accept", "text/html,application/xhtml+xml")
                    .setHeader("Accept-Language", "en-US,en;q=0.9")
                    .GET().build();
            String html = Http.send(req);
            if (html.length() > 400_000) {
                html = html.substring(0, 400_000);
            }
            return extractOg(html, pageUrl);
        } catch (Exception e) {
            System.err.println("WARN: could not read article page for photo (" + shorten(pageUrl) + "): " + e.getMessage());
            return null;
        }
    }

    static String extractOg(String html, String pageUrl) {
        Matcher m = OG_1.matcher(html);
        String found = m.find() ? m.group(1) : null;
        if (found == null) {
            m = OG_2.matcher(html);
            found = m.find() ? m.group(1) : null;
        }
        if (found == null) {
            return null;
        }
        found = found.replace("&amp;", "&").trim();
        try {
            return URI.create(pageUrl).resolve(found).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String shorten(String s) {
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }
}