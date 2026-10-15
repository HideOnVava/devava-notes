package com.devavaxp.notes;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
 * ones are kept.
 */
final class ErrorLog {

    static final String NAME = "registro.log";
    private static final int MOST = 256 * 1024;
    /** Null until the app starts it: the tests write no log. */
    private static Path file;

    private ErrorLog() {
    }

    /** Keeps the log in {@code file} from now on, under a line that says when, which version and on what. */
    static void start(Path file, String version) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > MOST) {   // the newest half, from a line's start
                byte[] all = Files.readAllBytes(file);
                int from = all.length - MOST / 2;
                while (from < all.length && all[from - 1] != '\n') from++;
                Files.write(file, Arrays.copyOfRange(all, from, all.length));
            }
        } catch (IOException e) {
            return;   // no log this time
        }
        ErrorLog.file = file;
        write("--- " + LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) + " · Devava Notes " + version + " · " + System.getProperty("os.name")
                + " " + System.getProperty("os.version") + " · Java " + System.getProperty("java.version"));
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            add("[" + thread.getName() + "] " + trace.toString().strip());
            if (before != null) before.uncaughtException(thread, e);
            else e.printStackTrace();
        });
    }

    /** One more line (or a few) for the log, after the time; nothing while there is no log. */
    static void add(String what) {
        write(LocalDateTime.now().toLocalTime().truncatedTo(ChronoUnit.SECONDS) + " " + what);
    }

    private static synchronized void write(String line) {
        if (file == null) return;
        try {
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // A log that cannot be written is no reason to stop.
        }
    }

    /** The log's file, once started. */
    static Path file() {
        return file;
    }

    /**
     * The last lines of a log, in at most {@code most} characters, with this user's folder said as
     * %USERPROFILE%: whatever is reported goes without the name of whoever uses this computer.
     */
    static String tail(Path log, int most) {
        String text;
        try {
            text = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).strip();
        } catch (IOException | RuntimeException e) {
            return "";
        }
        String home = System.getProperty("user.home");
        for (String form : new String[]{home, home.replace('\\', '/')}) {
            text = text.replaceAll("(?i)" + Pattern.quote(form), "%USERPROFILE%");
        }
        if (text.length() <= most) return text;
        int from = text.indexOf('\n', text.length() - most);
        return from < 0 ? text.substring(text.length() - most) : text.substring(from + 1);
    }
}
