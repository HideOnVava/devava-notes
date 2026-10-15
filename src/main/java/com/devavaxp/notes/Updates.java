package com.devavaxp.notes;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Whether GitHub has a newer version of the app than this one. Asked once, as the app opens, on a
 * thread of its own: offline, or if GitHub does not answer, nothing happens. Only the version is
 * sent (in the request's User-Agent), never anything of the notes.
 * <p>
 * GitHub's page of the latest release answers with where it moved: the page of its tag
 * (…/releases/tag/v2.2.0). Its API would say the same, but it answers only 60 times an hour to
 * each address, which the computers of a school's lab, behind one, use up.
 */
final class Updates {

    static final String LATEST = "https://github.com/HideOnVava/devava-notes/releases/latest";
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
            http.setInstanceFollowRedirects(false);
            http.setRequestMethod("HEAD");
            http.setConnectTimeout(5000);
            http.setReadTimeout(5000);
            http.setRequestProperty("User-Agent", "Devava-Notes/" + current);
            int code = http.getResponseCode();
            String moved = http.getHeaderField("Location");
            http.disconnect();
            return code / 100 == 3 && moved != null ? release(moved) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The release whose page GitHub moved to (…/releases/tag/v2.2.0), if it is one of the app's. */
    static Optional<Release> release(String page) {
        String tag = RELEASES + "tag/v";
        if (!page.startsWith(tag)) return Optional.empty();
        String version = page.substring(tag.length());
        return version.matches("\\d+(\\.\\d+)*") ? Optional.of(new Release(version, page)) : Optional.empty();
    }

    /** Whether version a comes after b (both numbers and dots), number by number: 2.10.0 after 2.9.1, however long the numbers. */
    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i].replaceFirst("^0+(?=\\d)", "") : "0", q = i < y.length ? y[i].replaceFirst("^0+(?=\\d)", "") : "0";
            int c = p.length() != q.length() ? Integer.compare(p.length(), q.length()) : p.compareTo(q);
            if (c != 0) return c > 0;
        }
        return false;
    }
}
