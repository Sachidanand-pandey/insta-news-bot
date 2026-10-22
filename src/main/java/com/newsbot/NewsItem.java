package com.newsbot;

import java.time.Instant;

/** One headline pulled from an RSS feed. */
record NewsItem(String title, String summary, String source, Instant published, String link) {

    /** Normalised title used to detect duplicates across feeds and across runs. */
    String key() {
        return title.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
