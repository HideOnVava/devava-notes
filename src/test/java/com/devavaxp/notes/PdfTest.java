package com.devavaxp.notes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfTest {

    private static final Pattern PAGE = Pattern.compile("/Type /Page\\b(?!s)");

    @Test
    void edgeIsWhereWindowsInstallsIt(@TempDir Path programs) throws IOException {
        assertNull(Pdf.edge(Map.<String, String>of()::get));
        assertNull(Pdf.edge(Map.of("ProgramFiles(x86)", programs.toString())::get));
        Path exe = Files.createFile(Files.createDirectories(programs.resolve("Microsoft").resolve("Edge").resolve("Application"))
                .resolve("msedge.exe"));
        assertEquals(exe, Pdf.edge(Map.of("ProgramFiles(x86)", "", "ProgramFiles", programs.toString())::get));
    }

    /** A title is a file name, but one made on a Mac may hold quotes or < >: it never ends a string, a style or a tag. */
    @Test
    void aTitleCannotBreakThePage() {
        String page = Pdf.page("Pilas \"</style><script>alert(1)</script>", "Estructura de Datos · Apunte", "<p>Hola</p>", false);
        assertFalse(page.contains("<script"), page);
        assertTrue(page.contains("script-src 'none'"), page);
        assertTrue(page.contains("<title>Pilas &quot;&lt;/style&gt;&lt;script&gt;alert(1)&lt;/script&gt;</title>"), page);
        assertTrue(page.contains("<h1>Pilas &quot;&lt;/style&gt;"), page);
        assertTrue(page.contains("<p>Estructura de Datos · Apunte</p>"), page);
        assertEquals("\"Sesión 5 \\b7  Pilas \\22 \\3c \\2f style\\3e \"", Pdf.cssString("Sesión 5 · Pilas \"</style>"));
        assertTrue(page.contains("<header class=\"paper\"><h1>"), page);
        assertTrue(page.contains("\n<p>Hola</p>\n</article>"), page);
    }

    @Test
    void onlyThePrintWindowRunsAScriptItsOwn() throws Exception {
        String page = Pdf.page("Pilas", "", "<p>Hola</p>", true);
        String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(Pdf.PRINT.getBytes(StandardCharsets.UTF_8)));
        assertTrue(page.contains("script-src 'sha256-" + hash + "';"), page);
        assertTrue(page.contains("<script>" + Pdf.PRINT + "</script>\n</body>"), page);
        assertEquals(1, page.split("<script", -1).length - 1, page);
        assertFalse(page.contains("<p></p>"), page);   // nothing under the title when there is nothing to say
    }

    /** Edge, as the app runs it: a PDF of every page, titled as the note, replacing an older one, and nothing of Edge left behind. */
    @Test
    void edgeMakesThePdf(@TempDir Path out) throws IOException {
        Path edge = Pdf.edge(System::getenv);
        assumeTrue(edge != null, "Microsoft Edge is not installed");
        List<Path> before = work();
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= 120; i++) body.append("<p>Párrafo ").append(i).append("</p>\n");
        Path pdf = Files.writeString(out.resolve("Sesión 5 · Pilas.pdf"), "an older one");
        Pdf.export(edge, Pdf.page("Sesión 5 · Pilas", "Estructura de Datos", body.toString(), false), pdf);
        String made = new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1);
        assertTrue(made.startsWith("%PDF-"), made.substring(0, Math.min(made.length(), 40)));
        assertTrue(PAGE.matcher(made).results().count() >= 3, "pages: " + PAGE.matcher(made).results().count());
        String title = "FEFF" + HexFormat.of().withUpperCase().formatHex("Sesión 5 · Pilas".getBytes(StandardCharsets.UTF_16BE));
        assertTrue(made.contains("/Title <" + title + ">"), "the PDF's title");
        assertFalse(made.contains("file:"), "a path of this computer");
        assertEquals(before, work(), "Edge's work folder, gone");
    }

    /** What Edge cannot do is said, with nothing left behind: here, a folder where the PDF cannot go. */
    @Test
    void aPdfThatCannotBeSavedSaysSo(@TempDir Path out) {
        Path edge = Pdf.edge(System::getenv);
        assumeTrue(edge != null, "Microsoft Edge is not installed");
        List<Path> before = work();
        Path nowhere = out.resolve("no existe").resolve("nota.pdf");
        IOException e = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> Pdf.export(edge, Pdf.page("Pilas", "", "<p>Hola</p>", false), nowhere));
        assertTrue(e.getMessage().contains("no existe"), e.toString());
        assertEquals(before, work(), "Edge's work folder, gone");
    }

    /** The work folders of Pdf in the temporary folder. */
    private static List<Path> work() {
        try (Stream<Path> all = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return all.filter(p -> p.getFileName().toString().startsWith("devava-notes-")).sorted().toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
