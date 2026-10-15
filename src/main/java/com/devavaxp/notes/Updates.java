package com.devavaxp.notes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Whether GitHub has a newer version of the app than this one. Asked once, as the app opens, on a
 * thread of its own: offline, or if GitHub does not answer, nothing happens. Only the version is
 * sent (in the request's User-Agent), never anything of the notes.
 */
final class Updates {

    static final String LATEST = "https://api.github.com/repos/HideOnVava/devava-notes/releases/latest";
    /** Where the app's releases are; a release's page must be one of them to be opened. */
    static final String RELEASES = "https://github.com/HideOnVava/devava-notes/releases/";

    /** A version published on GitHub ("2.2.0") and the page to download it from. */
    record Release(String version, String page) {
    }

    private Updates() {
    }

    /** Asks GitHub in the background; {@code found} gets the latest release when it is newer than {@code current}. */
    static void check(String current, Consumer<Release> found) {
        if (!current.matches("\\d+(\\.\\d+)*")) return;   // run from the code: no version to compare
        Thread asking = new Thread(() -> latest(current).filter(r -> newer(r.version(), current)).ifPresent(found), "Devava Notes: updates");
        asking.setDaemon(true);
        asking.start();
    }

    private static Optional<Release> latest(String current) {
        try {
            HttpURLConnection http = (HttpURLConnection) URI.create(LATEST).toURL().openConnection();
            http.setConnectTimeout(5000);
            http.setReadTimeout(5000);
            http.setRequestProperty("Accept", "application/vnd.github+json");
            http.setRequestProperty("User-Agent", "Devava-Notes/" + current);
            if (http.getResponseCode() != 200) return Optional.empty();
            try (InputStream in = http.getInputStream()) {
                return release(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The release GitHub's answer describes ({"tag_name": "v2.2.0", "html_url": …}), if it is one of the app's. */
    static Optional<Release> release(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String version = o.get("tag_name").getAsString().replaceFirst("^v", ""), page = o.get("html_url").getAsString();
            return version.matches("\\d+(\\.\\d+)*") && page.startsWith(RELEASES) ? Optional.of(new Release(version, page)) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Whether version a comes after b, number by number: 2.10.0 after 2.9.1. */
    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? Long.parseLong(x[i]) : 0, q = i < y.length ? Long.parseLong(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }
}
