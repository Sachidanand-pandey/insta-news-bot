package com.newsbot;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls headlines from RSS 2.0 feeds and picks a balanced, fresh, not-yet-posted set. */
final class NewsFetcher {

    record Feed(String name, String url) {}

    static final String DEFAULT_FEEDS = String.join(",",
            "The Hindu|https://www.thehindu.com/news/national/feeder/default.rss",
            "Indian Express|https://indianexpress.com/section/india/feed/",
            "Times of India|https://timesofindia.indiatimes.com/rssfeedstopstories.cms");

    /** Parses "Name|url,Name|url" (the NEWS_FEEDS format). */
    static List<Feed> parseFeeds(String spec) {
        List<Feed> feeds = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] bits = part.trim().split("\\|", 2);
            if (bits.length == 2) {
                feeds.add(new Feed(bits[0].trim(), bits[1].trim()));
            }
        }
        return feeds;
    }

    /** Fetches every feed; a feed that fails is logged and skipped so one outage never blocks a post. */
    List<NewsItem> fetchAll(List<Feed> feeds, int perFeed) {
        List<NewsItem> all = new ArrayList<>();
        for (Feed feed : feeds) {
            try {
                all.addAll(fetch(feed, perFeed));
            } catch (Exception e) {
                System.err.println("WARN: feed '" + feed.name() + "' failed: " + e.getMessage());
            }
        }
        return all;
    }

    private List<NewsItem> fetch(Feed feed, int limit) throws Exception {
        String xml;
        if (feed.url().startsWith("file:")) {
            xml = Files.readString(Path.of(URI.create(feed.url())));
        } else {
            xml = Http.sendWithRetry(Http.request(feed.url()).GET().build(), 3);
        }

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        List<NewsItem> out = new ArrayList<>();
        NodeList items = doc.getElementsByTagName("item");
        for (int i = 0; i < items.getLength() && out.size() < limit; i++) {
            Element item = (Element) items.item(i);
            String title = clean(text(item, "title"));
            if (title.isBlank()) {
                continue;
            }
            String rawDescription = text(item, "description");
            String summary = clean(rawDescription);
            if (summary.equalsIgnoreCase(title)) {
                summary = "";
            }
            out.add(new NewsItem(title, shorten(summary, 210), feed.name(),
                    parseDate(text(item, "pubDate")), text(item, "link"),
                    findImage(item, rawDescription)));
        }
        return out;
    }

    /**
     * Newest first, de-duplicated, skipping anything already posted or older than maxAgeHours,
     * then picked round-robin across sources so one outlet doesn't dominate the post.
     */
    List<NewsItem> select(List<NewsItem> items, Set<String> alreadyPosted, int count, int maxAgeHours) {
        Instant cutoff = Instant.now().minus(Duration.ofHours(maxAgeHours));
        Set<String> seen = new LinkedHashSet<>(alreadyPosted);

        Map<String, Deque<NewsItem>> bySource = new LinkedHashMap<>();
        items.stream()
                .filter(n -> n.published().isAfter(cutoff))
                .sorted(Comparator.comparing(NewsItem::published).reversed())
                .filter(n -> seen.add(n.key()))
                .forEach(n -> bySource.computeIfAbsent(n.source(), k -> new ArrayDeque<>()).add(n));

        List<NewsItem> picked = new ArrayList<>();
        while (picked.size() < count && bySource.values().stream().anyMatch(d -> !d.isEmpty())) {
            for (Deque<NewsItem> d : bySource.values()) {
                if (!d.isEmpty() && picked.size() < count) {
                    picked.add(d.poll());
                }
            }
        }
        picked.sort(Comparator.comparing(NewsItem::published).reversed());
        return picked;
    }

    private static final Pattern IMG_EXT = Pattern.compile("\\.(jpe?g|png|webp)(\\?.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_TAG = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /**
     * Looks for a photo inside an RSS item: media:content, media:thumbnail, enclosure,
     * then the first <img> in the description / content:encoded. Returns "" when there is none.
     */
    static String findImage(Element item, String rawDescription) {
        for (String tag : new String[] {"media:content", "media:thumbnail", "enclosure"}) {
            NodeList nodes = item.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                String url = e.getAttribute("url").trim();
                if (url.isEmpty()) {
                    continue;
                }
                String type = (e.getAttribute("type") + e.getAttribute("medium")).toLowerCase();
                boolean isImage = type.contains("image") || (type.isEmpty() && IMG_EXT.matcher(url).find());
                if (isImage && url.startsWith("http")) {
                    return url;
                }
            }
        }
        for (String html : new String[] {rawDescription, text(item, "content:encoded")}) {
            Matcher m = IMG_TAG.matcher(html == null ? "" : html);
            if (m.find()) {
                String url = m.group(1).replace("&amp;", "&").trim();
                if (url.startsWith("//")) {
                    url = "https:" + url;
                }
                if (url.startsWith("http")) {
                    return url;
                }
            }
        }
        return "";
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return "";
        }
        Node n = nodes.item(0);
        return n.getTextContent() == null ? "" : n.getTextContent().trim();
    }

    private static String clean(String s) {
        return s.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int cut = s.lastIndexOf(' ', max);
        return s.substring(0, cut > max / 2 ? cut : max).trim() + "…";
    }

    private static Instant parseDate(String s) {
        if (s == null || s.isBlank()) {
            return Instant.now();
        }
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (Exception ignored) {
            return Instant.now();
        }
    }
}