package com.newsbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Usage: Main [morning|evening|auto]
 * Fetches fresh headlines, renders a carousel with Selenium, uploads the slides and publishes to Instagram.
 * Set DRY_RUN=true to render the slides and print the caption without posting.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        ZoneId zone = ZoneId.of(Config.get("BOT_TZ", "Asia/Kolkata"));
        ZonedDateTime now = ZonedDateTime.now(zone);

        String edition = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "auto";
        if (edition.equals("auto")) {
            edition = now.getHour() < 12 ? "morning" : "evening";
        }
        String editionLabel = edition.equals("morning") ? "Morning Brief" : "Evening Brief";
        String dateLabel = now.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
        boolean dryRun = Config.getBool("DRY_RUN", false);
        String brand = Config.get("BRAND_NAME", "Daily News");
        int storyCount = Config.getInt("STORY_COUNT", 4);

        // 1. Fetch and select
        NewsFetcher fetcher = new NewsFetcher();
        PostedStore store = new PostedStore(Path.of(Config.get("STATE_FILE", "state/posted.txt")));
        List<NewsItem> all = fetcher.fetchAll(
                NewsFetcher.parseFeeds(Config.get("NEWS_FEEDS", NewsFetcher.DEFAULT_FEEDS)), 8);
        List<NewsItem> stories = fetcher.select(all, store.keys(), storyCount, Config.getInt("MAX_AGE_HOURS", 24));

        if (stories.isEmpty()) {
            System.out.println("No new stories to post for the " + edition + " edition. Nothing published.");
            return;
        }
        System.out.println("Selected " + stories.size() + " stories for the " + edition + " edition:");
        stories.forEach(s -> System.out.println(" - [" + s.source() + "] " + s.title()));

        // 2. Render slides with Selenium
        Path outDir = Path.of("output", LocalDate.now(zone) + "-" + edition);
        List<Path> slides = new ArrayList<>();
        try (CardRenderer renderer = new CardRenderer(outDir)) {
            slides.add(renderer.render(CardTemplates.cover(brand, editionLabel, dateLabel, stories.size()), "slide-0-cover"));
            for (int i = 0; i < stories.size(); i++) {
                slides.add(renderer.render(CardTemplates.news(brand, i + 1, stories.size(), stories.get(i)),
                        "slide-" + (i + 1)));
            }
            slides.add(renderer.render(CardTemplates.outro(brand), "slide-9-outro"));
        }

        String caption = buildCaption(editionLabel, dateLabel, stories);
        System.out.println("\nCaption:\n" + caption + "\n");

        if (dryRun) {
            System.out.println("DRY_RUN=true: " + slides.size() + " slides saved in " + outDir + ", nothing published.");
            return;
        }

        // 3. Upload and publish
        ImageHost host = new ImageHost(Config.require("IMGBB_API_KEY"));
        List<String> urls = new ArrayList<>();
        for (Path slide : slides) {
            urls.add(host.upload(slide));
        }

        InstagramPublisher instagram = new InstagramPublisher(
                Config.get("IG_GRAPH_HOST", "graph.instagram.com"),
                Config.get("IG_GRAPH_VERSION", "v26.0"),
                Config.require("IG_USER_ID"),
                Config.require("IG_ACCESS_TOKEN"));
        String mediaId = instagram.publishCarousel(urls, caption);
        System.out.println("Published to Instagram. Media id: " + mediaId);

        // Only remember the headlines once the post is really live
        store.addAndSave(stories);
    }

    static String buildCaption(String editionLabel, String dateLabel, List<NewsItem> stories) {
        String icon = editionLabel.startsWith("Morning") ? "🌅" : "🌆";
        StringBuilder sb = new StringBuilder();
        sb.append(icon).append(' ').append(editionLabel).append(" | ").append(dateLabel).append("\n\n");
        for (int i = 0; i < stories.size(); i++) {
            sb.append(i + 1).append(". ").append(stories.get(i).title()).append("\n\n");
        }
        List<String> sources = stories.stream().map(NewsItem::source).distinct().toList();
        sb.append("Sources: ").append(String.join(", ", sources)).append("\n\n");
        sb.append("Follow for your daily news update 📲\n\n");
        sb.append(Config.get("HASHTAGS", "#news #todaynews #breakingnews #india #dailynews #currentaffairs #headlines"));
        String caption = sb.toString();
        return caption.length() <= 2200 ? caption : caption.substring(0, 2190) + "…";
    }
}
