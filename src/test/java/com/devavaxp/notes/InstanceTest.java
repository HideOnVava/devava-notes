package com.devavaxp.notes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One window per notes folder, the error log and what is reported of it, and newer versions on GitHub. */
class InstanceTest {

    @AfterEach
    void letGo() {
        Instance.release();
        ErrorLog.stop();
    }

    /** A second launch on the same notes shows the first window instead; on other notes it opens; once the first ends, it opens. */
    @Test
    void oneWindowPerNotesFolder(@TempDir Path local, @TempDir Path notes, @TempDir Path others) throws Exception {
        CountDownLatch shown = new CountDownLatch(1);
        assertTrue(Instance.claim(local, notes, shown::countDown));
        assertFalse(Instance.claim(local, notes, () -> {
        }));
        assertTrue(shown.await(5, TimeUnit.SECONDS), "the first window was told to show itself");
        assertTrue(Instance.claim(local, others, () -> {
        }));
        Instance.release();   // the first one ended
        assertTrue(Instance.claim(local, notes, () -> {
        }));
    }

    /**
     * When the lock is held but what listens on its port is not the app (an old port another program
     * took since), or nothing does: the launch waits for a while, as for an app that is starting, and
     * opens. A program that knocks without saying hello does not bring the window forward.
     */
    @Test
    void anotherProgramOnThePortIsNotTheApp(@TempDir Path local, @TempDir Path notes) throws Exception {
        CountDownLatch shown = new CountDownLatch(1);
        assertTrue(Instance.claim(local, notes, shown::countDown));
        Path portFile = only(local, ".port"), lockFile = only(local, ".lock");
        try (Socket rude = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(Files.readString(portFile)))) {
            rude.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            assertEquals(-1, rude.getInputStream().read(), "no answer for another program");
        }
        assertFalse(shown.await(500, TimeUnit.MILLISECONDS), "nor the window brought forward");
        Instance.release();

        try (ServerSocket other = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
             FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
             FileLock held = channel.lock()) {
            Thread answering = new Thread(() -> {
                while (true) {
                    try (Socket s = other.accept()) {
                        s.getOutputStream().write("HTTP/1.1 400 Bad Request\r\n".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            answering.start();
            Files.writeString(portFile, String.valueOf(other.getLocalPort()));
            long start = System.currentTimeMillis();
            assertTrue(held.isValid());
            assertTrue(Instance.claim(local, notes, () -> {
            }), "a launch that cannot reach the app opens");
            assertTrue(System.currentTimeMillis() - start >= 2500, "after waiting for it to start");
        }
    }

    /** An old port file, with no launch holding the lock: no reason not to open, and at once. */
    @Test
    void anOldPortFileIsNoReasonNotToOpen(@TempDir Path local, @TempDir Path notes) throws Exception {
        assertTrue(Instance.claim(local, notes, () -> {
        }));
        Path portFile = only(local, ".port");
        Instance.release();
        Files.writeString(portFile, "nada");
        long start = System.currentTimeMillis();
        assertTrue(Instance.claim(local, notes, () -> {
        }));
        assertTrue(System.currentTimeMillis() - start < 1000);
        assertTrue(Files.readString(portFile).matches("\\d+"), "its own port written");
    }

    /** The log keeps the errors with the time, once each in a row, and a session's no further than so much. */
    @Test
    void theLogKeepsErrors(@TempDir Path local) throws Exception {
        Path log = local.resolve(ErrorLog.NAME);
        ErrorLog.start(log, "9.9.9");
        ErrorLog.add("No se pudo guardar Pilas.md");
        ErrorLog.add("No se pudo guardar Pilas.md");   // again and again, while OneDrive holds it
        ErrorLog.add("[editor.js] TypeError");
        String[] lines = Files.readString(log).split("\n");
        assertTrue(lines[0].startsWith("--- ") && lines[0].contains("Devava Notes 9.9.9"), lines[0]);
        assertEquals(3, lines.length, String.join("\n", lines));
        assertTrue(lines[1].matches("\\d\\d:\\d\\d:\\d\\d No se pudo guardar Pilas.md"), lines[1]);

        for (int i = 0; i < 5000; i++) ErrorLog.add("[JavaFX Application Thread] java.lang.NullPointerException número " + i);
        String text = Files.readString(log);
        assertTrue(text.length() < 140 * 1024, "a session that keeps failing stops writing: " + text.length());
        assertTrue(text.endsWith("… (más errores en esta sesión, sin guardar)\n"));
    }

    /** A log grown too big keeps its newest half when the app starts, from a line's start, however big it was. */
    @Test
    void theLogKeepsItsNewestHalf(@TempDir Path local) throws Exception {
        Path log = local.resolve(ErrorLog.NAME);
        Files.writeString(log, IntStream.range(0, 40_000).mapToObj(i -> "línea " + i + "\n").collect(Collectors.joining()));
        ErrorLog.start(log, "9.9.9");
        String text = Files.readString(log);
        assertTrue(text.length() <= 130 * 1024 && text.length() > 100 * 1024, String.valueOf(text.length()));
        assertTrue(text.startsWith("línea "), text.substring(0, 20));
        assertTrue(text.contains("línea 39999\n--- "), "the newest lines, then this session");
    }

    /** What is reported of the log goes without this user's folder, in any of the forms a path takes. */
    @Test
    void whatIsReportedSaysNoUsersName(@TempDir Path local) throws Exception {
        String home = "C:\\Users\\Ana María";
        String text = """
                No se pudo guardar C:\\Users\\Ana María\\OneDrive\\Documentos\\Devava Notes\\Pilas.md
                file:///C:/Users/Ana%20María/OneDrive/x.png y c:/users/ANA MARÍA/x.png
                {"path": "C:\\\\Users\\\\Ana María\\\\x"}""";
        String said = ErrorLog.anonymous(text, home);
        assertFalse(said.toLowerCase().contains("ana"), said);
        assertTrue(said.contains("%USERPROFILE%\\OneDrive\\Documentos") && said.contains("file:///%USERPROFILE%/OneDrive"), said);

        Path log = local.resolve("short.log");
        Files.writeString(log, "a\nb\nc\n");
        assertEquals("b\nc", ErrorLog.tail(log, 4));
        assertEquals("", ErrorLog.tail(local.resolve("none.log"), 100));
    }

    /** The link to report a problem fits what Windows passes on whole, with the newest lines of the log, accents and all. */
    @Test
    void theLinkToReportAProblemFits() {
        String log = IntStream.rangeClosed(1, 60).mapToObj(i -> "12:00:0" + (i % 10) + " Ñandú: acción fallida, línea " + i)
                .collect(Collectors.joining("\n"));
        String link = NotesApp.issueLink("9.9.9", log);
        assertTrue(link.startsWith("https://github.com/HideOnVava/devava-notes/issues/new?body="), link);
        assertTrue(link.length() <= NotesApp.LONGEST_LINK, String.valueOf(link.length()));
        String body = URLDecoder.decode(link.substring(link.indexOf("body=") + 5), StandardCharsets.UTF_8);
        assertTrue(body.startsWith("**Qué pasó**") && body.contains("Devava Notes 9.9.9 · "), body);
        assertTrue(body.contains("línea 60\n```") && !body.contains("línea 1\n"), "the newest lines that fit: " + body);
        assertFalse(link.contains(" ") || link.contains("\n"), "nothing a command line would split");
        String bare = URLDecoder.decode(NotesApp.issueLink("9.9.9", "").substring(link.indexOf("body=") + 5), StandardCharsets.UTF_8);
        assertFalse(bare.contains("Registro de errores"), "no log, no section for it");
    }

    /** A newer version, number by number; GitHub's page of the latest release moves to its tag's. */
    @Test
    void aNewerVersionOnGitHub() {
        assertTrue(Updates.newer("2.2.0", "2.1.0"));
        assertTrue(Updates.newer("2.10.0", "2.9.1"));
        assertTrue(Updates.newer("3", "2.9.9"));
        assertTrue(Updates.newer("99999999999999999999.0", "1.0"));
        assertFalse(Updates.newer("2.1.0", "2.1.0"));
        assertFalse(Updates.newer("2.1", "2.1.0"));
        assertFalse(Updates.newer("02.1", "2.1"));
        assertFalse(Updates.newer("2.0.9", "2.1.0"));
        String page = "https://github.com/HideOnVava/devava-notes/releases/tag/v2.2.0";
        assertEquals(new Updates.Release("2.2.0", page), Updates.release(page).orElseThrow());
        assertTrue(Updates.release(page + "-beta").isEmpty());
        assertTrue(Updates.release("https://github.com/HideOnVava/devava-notes/releases").isEmpty(), "no release yet");
        assertTrue(Updates.release("https://example.com/HideOnVava/devava-notes/releases/tag/v9.9.9").isEmpty());
        assertTrue(Updates.release("/HideOnVava/devava-notes/releases/tag/v2.2.0").isEmpty());
    }

    private static Path only(Path dir, String extension) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.toString().endsWith(extension)).findFirst().orElseThrow();
        }
    }
}
