package com.devavaxp.notes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceTest {

    /** A second launch on the same notes shows the first window instead; on other notes it opens. */
    @Test
    void oneWindowPerNotesFolder(@TempDir Path local, @TempDir Path notes, @TempDir Path others) throws Exception {
        CountDownLatch shown = new CountDownLatch(1);
        assertTrue(Instance.claim(local, notes, shown::countDown));
        assertFalse(Instance.claim(local, notes, () -> {
        }));
        assertTrue(shown.await(5, TimeUnit.SECONDS), "the first window was told to show itself");
        assertTrue(Instance.claim(local, others, () -> {
        }));
    }

    /** The log keeps the errors with the time, and what is reported of it says no user's name. */
    @Test
    void theLogKeepsErrorsAndReportsThemWithoutTheUsersName(@TempDir Path local) throws Exception {
        Path log = local.resolve(ErrorLog.NAME);
        ErrorLog.start(log, "9.9.9");
        ErrorLog.add("No se pudo guardar " + Path.of(System.getProperty("user.home"), "Documents", "Devava Notes", "Pilas.md"));
        String text = Files.readString(log);
        assertTrue(text.startsWith("--- ") && text.contains("Devava Notes 9.9.9"), text);
        String tail = ErrorLog.tail(log, 600);
        assertTrue(tail.contains("No se pudo guardar %USERPROFILE%"), tail);
        assertFalse(tail.contains(System.getProperty("user.home")), tail);
        assertEquals("b\nc", ErrorLog.tail(writeLines(local.resolve("short.log")), 4));

        String link = NotesApp.issueLink("9.9.9", tail);
        assertTrue(link.startsWith("https://github.com/HideOnVava/devava-notes/issues/new?body=") && link.contains("9.9.9"), link);
        assertTrue(link.length() < 2000, "a link Windows opens whole: " + link.length());
    }

    /** GitHub's latest release, read from its answer, is newer number by number; only the app's own pages count. */
    @Test
    void aNewerVersionOnGitHub() {
        assertTrue(Updates.newer("2.2.0", "2.1.0"));
        assertTrue(Updates.newer("2.10.0", "2.9.1"));
        assertTrue(Updates.newer("3", "2.9.9"));
        assertFalse(Updates.newer("2.1.0", "2.1.0"));
        assertFalse(Updates.newer("2.1", "2.1.0"));
        assertFalse(Updates.newer("2.0.9", "2.1.0"));
        assertEquals(new Updates.Release("2.2.0", "https://github.com/HideOnVava/devava-notes/releases/tag/v2.2.0"), Updates.release(
                "{\"tag_name\": \"v2.2.0\", \"html_url\": \"https://github.com/HideOnVava/devava-notes/releases/tag/v2.2.0\", \"draft\": false}").orElseThrow());
        assertTrue(Updates.release("{\"tag_name\": \"v2.2.0\", \"html_url\": \"https://example.com/v2.2.0\"}").isEmpty());
        assertTrue(Updates.release("{\"message\": \"Not Found\"}").isEmpty());
        assertTrue(Updates.release("<html>").isEmpty());
    }

    private static Path writeLines(Path file) throws Exception {
        return Files.writeString(file, "a\nb\nc\n");
    }
}
