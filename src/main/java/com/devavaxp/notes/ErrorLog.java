package com.devavaxp.notes;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * The errors of the app (its own, the editor's and the drawing window's, and what it failed to do)
 * in a file of this computer, a session after another, to report a problem with. Only the newest
 * are kept, and a session that keeps failing writes no more than so much.
 */
final class ErrorLog {

    static final String NAME = "registro.log";
    /** Past this, the log keeps its newest half when the app starts. */
    private static final int MOST = 256 * 1024;
    /** What a session writes at most: an error repeated forever must not fill the disk. */
    private static final int SESSION = 128 * 1024;
    /** Null until the app starts it: the tests write no log. */
    private static Path file;
    private static String last;
    private static int written;
    private static boolean handled;

    private ErrorLog() {
    }

    /** Keeps the log in {@code file} from now on, under a line that says when, which version and on what. */
    static synchronized void start(Path file, String version) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > MOST) keepNewest(file);
        } catch (IOException e) {
            return;   // no log this time
        }
        ErrorLog.file = file;
        last = null;
        written = 0;
        write("--- " + LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) + " · Devava Notes " + version + " · " + System.getProperty("os.name")
                + " " + System.getProperty("os.version") + " · Java " + System.getProperty("java.version"));
        if (handled) return;
        handled = true;
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            add("[" + thread.getName() + "] " + trace.toString().strip());
            if (before != null) before.uncaughtException(thread, e);
            else e.printStackTrace();
        });
    }

    /** No more log (for the tests, which start one of their own). */
    static synchronized void stop() {
        file = null;
    }

    /** The newest half of the log, from the start of a line: read from its end, however big it got. */
    private static void keepNewest(Path log) throws IOException {
        ByteBuffer kept = ByteBuffer.allocate(MOST / 2);
        try (SeekableByteChannel in = Files.newByteChannel(log)) {
            in.position(in.size() - kept.capacity());
            while (kept.hasRemaining() && in.read(kept) >= 0) {
                // until the buffer is full
            }
        }
        byte[] bytes = kept.array();
        int from = 0;
        while (from < kept.position() && bytes[from] != '\n') from++;
        Files.write(log, Arrays.copyOfRange(bytes, Math.min(from + 1, kept.position()), kept.position()));
    }

    /** One more line (or a few) for the log, after the time; nothing while there is no log, nor the same again. */
    static synchronized void add(String what) {
        if (file == null || what.equals(last)) return;
        last = what;
        write(LocalDateTime.now().toLocalTime().truncatedTo(ChronoUnit.SECONDS) + " " + what);
    }

    private static synchronized void write(String line) {
        if (file == null || written > SESSION) return;
        String text = line + "\n";
        written += text.length();
        if (written > SESSION) text += "… (más errores en esta sesión, sin guardar)\n";
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // A log that cannot be written is no reason to stop.
        }
    }

    /** The log's file, once started. */
    static synchronized Path file() {
        return file;
    }

    /**
     * The last lines of a log, in at most {@code most} characters, with this user's folder said as
     * %USERPROFILE% (in every form a path takes: \, /, %20, \\): what is reported goes without the
     * name of whoever uses this computer.
     */
    static String tail(Path log, int most) {
        String text;
        try {
            text = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).strip();
        } catch (IOException | RuntimeException e) {
            return "";
        }
        text = anonymous(text, System.getProperty("user.home"));
        if (text.length() <= most) return text;
        int from = text.indexOf('\n', text.length() - most);
        return from < 0 ? text.substring(text.length() - most) : text.substring(from + 1);
    }

    /** A text with a user's folder, in any of the forms a path takes, said as %USERPROFILE%. */
    static String anonymous(String text, String home) {
        String slashed = home.replace('\\', '/');
        for (String form : new String[]{home.replace("\\", "\\\\"), home, slashed, slashed.replace(" ", "%20")}) {
            text = Pattern.compile(Pattern.quote(form), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).replaceAll("%USERPROFILE%");
        }
        return text;
    }
}
