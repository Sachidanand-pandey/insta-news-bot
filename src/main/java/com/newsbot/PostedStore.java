package com.newsbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Remembers which headlines were already posted so the 6 PM post never repeats the 6 AM one.
 * Stored as a plain text file (one normalised title per line); keeps the most recent 500.
 */
final class PostedStore {
    private static final int MAX_KEPT = 500;
    private final Path file;
    private final Set<String> keys = new LinkedHashSet<>();

    PostedStore(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            keys.addAll(Files.readAllLines(file));
        }
    }

    Set<String> keys() {
        return Set.copyOf(keys);
    }

    void addAndSave(List<NewsItem> items) throws IOException {
        items.forEach(i -> keys.add(i.key()));
        List<String> all = new ArrayList<>(keys);
        List<String> kept = all.subList(Math.max(0, all.size() - MAX_KEPT), all.size());
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, kept);
    }
}
