package com.devavaxp.notes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.devavaxp.notes.Text.t;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    /** A literal shown through t(…), in the sources; "%s" and the like fill in what changes. */
    private static final Pattern SHOWN = Pattern.compile("\\bt\\(\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern FORMAT = Pattern.compile("%[sd]");

    @AfterEach
    void backToEnglish() {
        Text.use(Text.ENGLISH);
    }

    @Test
    void everyTextTheAppShowsHasItsSpanish() throws Exception {
        List<String> missing = new ArrayList<>();
        int found = 0;
        try (Stream<Path> sources = Files.list(Path.of("src/main/java/com/devavaxp/notes"))) {
            for (Path source : sources.toList()) {
                Matcher m = SHOWN.matcher(Files.readString(source));
                while (m.find()) {
                    found++;
                    String english = m.group(1);
                    // "+ New " + a kind: each kind's phrase is checked below.
                    if (!Text.translated(english) && !english.equals("+ New ") && !english.equals("New ")) missing.add(english);
                }
            }
        }
        assertTrue(found > 120, found + " texts found: the pattern no longer sees the code's t(\"…\")");
        for (NotebookType type : NotebookType.ALL) {
            shown(type.name(), missing);
            shown(type.description(), missing);
            type.views().forEach(v -> shown(v.name(), missing));
            for (NotebookType.Kind kind : type.kinds()) {
                shown(kind.name(), missing);
                shown("New " + kind.name().toLowerCase(Locale.ROOT), missing);
                kind.template().lines().filter(l -> l.startsWith("## ") || l.startsWith("| C")).forEach(l -> shown(l, missing));
                for (NotebookType.Field f : kind.fields()) {
                    shown(f.label(), missing);
                    if (f.input() == NotebookType.Input.CHOICE) f.options().forEach(o -> shown(o, missing));
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    /** Words that are the same in both languages: names, and ID. */
    private static void shown(String english, List<String> missing) {
        boolean same = List.of("General", "ID", "## Idea", "Codeforces", "AtCoder", "LeetCode").contains(english);
        if (!same && !Text.translated(english) && !missing.contains(english)) missing.add(english);
    }

    @Test
    void aTranslationFillsInWhatItsEnglishDoes() {
        for (Map.Entry<String, String> e : Text.spanish().entrySet()) {
            assertEquals(FORMAT.matcher(e.getKey()).results().map(r -> r.group()).toList(),
                    FORMAT.matcher(e.getValue()).results().map(r -> r.group()).toList(), e.getKey());
        }
    }

    @Test
    void speaksTheLanguageItIsTold() {
        assertEquals("All notes", t("All notes"));
        assertEquals("3 days late", t("%d days late", 3));
        Text.use(Text.SPANISH);
        assertEquals("Todas las notas", t("All notes"));
        assertEquals("3 días de retraso", t("%d days late", 3));
        assertEquals("Unknown words stay", t("Unknown words stay"));
        assertEquals("---\nstatus: To do\n---\n## Enunciado\n\n```plsql\n\n```\n",
                Text.template("---\nstatus: To do\n---\n## Statement\n\n```plsql\n\n```\n"));   // what the note keeps stays English
        Text.use("fr");
        assertEquals("All notes", t("All notes"));
    }
}
