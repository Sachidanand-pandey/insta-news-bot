package com.newsbot;

import java.time.Instant;

/** One headline pulled from an RSS feed. imageUrl may be blank when the feed carries no photo. */
record NewsItem(String title, String summary, String source, Instant published, String link, String imageUrl) {

    /** Normalised title used to detect duplicates across feeds and across runs. */
    String key() {
        return title.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    NewsItem withImageUrl(String url) {
        return new NewsItem(title, summary, source, published, link, url == null ? "" : url);
    }
}