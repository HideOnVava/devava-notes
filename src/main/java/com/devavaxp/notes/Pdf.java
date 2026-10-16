package com.devavaxp.notes;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A note on paper, as Vista previa shows it: Microsoft Edge, which comes with Windows 10 and 11, makes
 * its PDF (with no window) or opens its print window, from a page made for paper (editor.css, "Paper").
 * Edge starts each time with a profile of its own, deleted afterwards, and is told not to sign in with
 * the Windows account, as it otherwise does by itself in a new profile.
 */
final class Pdf {

    /** In the print window's page: Edge's print preview as it opens, and the window gone once printed or cancelled. */
    static final String PRINT = "addEventListener('load', () => print()); addEventListener('afterprint', () => close());";

    /** How long Edge may take to make a PDF; it takes about a second. */
    private static final long PATIENCE = 60;
    /** How long an Edge that took over the work may take to show up (a tenth of a second, as seen), in nanoseconds. */
    private static final long SHOWING_UP = TimeUnit.SECONDS.toNanos(3);

    private Pdf() {
    }

    /**
     * Edge, where Windows installs it (or where one user installed it alone); null when it is not there.
     * {@code env} reads a variable of the environment, as System::getenv does whatever their case.
     */
    static Path edge(Function<String, String> env) {
        for (String folder : List.of("ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA")) {
            String base = env.apply(folder);
            if (base == null || base.isBlank()) continue;
            Path exe = Path.of(base, "Microsoft", "Edge", "Application", "msedge.exe");
            if (Files.isRegularFile(exe)) return exe;
        }
        return null;
    }

    /**
     * The page Edge prints: the note's title and what it is above its body (its HTML for paper, from
     * Editor.printable), in the light theme, with the title at the foot of each page. Nothing in it
     * runs but the print window's own script.
     */
    static String page(String title, String about, String body, boolean print) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                <meta charset="utf-8">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src %s; \
                style-src file: 'unsafe-inline'; img-src file: data: http: https:; font-src file: data:">
                <title>%s</title>
                <link rel="stylesheet" href="%s">
                <link rel="stylesheet" href="%s">
                <style>@page { @bottom-left { content: %s; } }</style>
                </head>
                <body>
                <article id="reading">
                <header class="paper"><h1>%s</h1>%s</header>
                %s
                </article>
                %s</body>
                </html>
                """.formatted(print ? "'" + hash(PRINT) + "'" : "'none'", Editor.escape(title),
                Editor.escape(Editor.page("katex/katex.min.css")), Editor.escape(Editor.page("editor.css")), cssString(title),
                Editor.escape(title), about.isEmpty() ? "" : "<p>" + Editor.escape(about) + "</p>", body,
                print ? "<script>" + PRINT + "</script>\n" : "");
    }

    /** Text as a CSS string, all but its letters, digits and spaces as their codes ("\22 " for "): nothing in it ends the string or the style. */
    static String cssString(String text) {
        StringBuilder css = new StringBuilder("\"");
        text.codePoints().forEach(c -> {
            if (Character.isLetterOrDigit(c) || c == ' ') css.appendCodePoint(c);
            else css.append('\\').append(Integer.toHexString(c)).append(' ');
        });
        return css.append('"').toString();
    }

    /** The PDF of a page from page() at {@code pdf}, replacing what was there. */
    static void export(Path edge, String page, Path pdf) throws IOException {
        Path work = Files.createTempDirectory("devava-notes-");
        try {
            Path html = Files.writeString(work.resolve("nota.html"), page);
            Path made = work.resolve("nota.pdf");
            Process process = start(edge, work, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    "--print-to-pdf=" + made, html.toUri().toString());
            if (!done(process, work, Duration.ofSeconds(PATIENCE), () -> Files.exists(made))) {
                stop(process);
                throw new IOException("Edge tardó demasiado en hacerlo");
            }
            if (!Files.isRegularFile(made) || Files.size(made) == 0) {
                ErrorLog.add("Edge made no PDF (exit " + process.exitValue() + "): " + output(work));
                throw new IOException("Edge no pudo hacerlo. Prueba con Imprimir… y elige «Guardar como PDF».");
            }
            Files.move(made, pdf, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("se interrumpió");
        } finally {
            delete(work);
        }
    }

    /** Edge's print window for a page from page(…, true): its preview, the printer, the copies; it closes once done. */
    static void print(Path edge, String page) throws IOException {
        Path work = Files.createTempDirectory("devava-notes-");
        try {
            Path html = Files.writeString(work.resolve("nota.html"), page);
            Process process = start(edge, work, "--app=" + html.toUri());
            // ponytail: if the app closes first, this stays in %TEMP% (Windows cleans it up); a sweep at start if it piles up
            Thread cleaner = new Thread(() -> {
                try {
                    done(process, work, Duration.ofDays(7), () -> false);
                } catch (InterruptedException e) {
                    return;
                }
                delete(work);
            }, "Devava Notes: print window");
            cleaner.setDaemon(true);
            cleaner.start();
        } catch (IOException e) {
            delete(work);
            throw e;
        }
    }

    /**
     * Whether Edge is done in its work folder before {@code patience} runs out: once {@code made}, or
     * once Edge is gone. The process started may hand its work to another of Edge's (as it does when
     * started from some shells, or to finish an update) and end at once; that one holds the profile's
     * lockfile while it runs, and takes a moment to show up.
     */
    private static boolean done(Process process, Path work, Duration patience, BooleanSupplier made) throws InterruptedException {
        long start = System.nanoTime(), limit = patience.toNanos();
        if (!process.waitFor(limit, TimeUnit.NANOSECONDS)) return false;
        Path lock = work.resolve("edge").resolve("lockfile");
        long seen = System.nanoTime();   // when Edge was last seen at work
        while (true) {
            long now = System.nanoTime();
            if (Files.exists(lock)) seen = now;
            else if (made.getAsBoolean() || now - seen > SHOWING_UP) return true;
            if (now - start > limit) return false;
            Thread.sleep(50);
        }
    }

    /** Edge with a new profile in the work folder, kept from signing in to the Windows account and from syncing. */
    private static Process start(Path edge, Path work, String... arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of(edge.toString(), "--user-data-dir=" + work.resolve("edge"),
                "--no-first-run", "--no-default-browser-check", "--disable-sync", "--disable-features=msImplicitSignin"));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(work.resolve("edge.log").toFile()).start();
    }

    /** Edge and its helpers, ended. */
    private static void stop(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
    }

    /** The end of what Edge printed, for the error log. */
    private static String output(Path work) {
        try {
            String log = Files.readString(work.resolve("edge.log"), StandardCharsets.UTF_8).strip();
            return log.substring(Math.max(0, log.length() - 600));
        } catch (IOException | UncheckedIOException e) {
            return "";
        }
    }

    /** The work folder, Edge's profile in it; Edge's helpers may hold a file a moment after it ends. */
    private static void delete(Path work) {
        for (int attempt = 0; attempt < 20 && Files.exists(work); attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try (Stream<Path> files = Files.walk(work)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException | UncheckedIOException e) {
                // A file went away, or is still held: the next attempt sees.
            }
        }
    }

    private static String hash(String script) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8));
            return "sha256-" + Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every Java has SHA-256
        }
    }
}
