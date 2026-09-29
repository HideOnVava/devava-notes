package com.devavaxp.notes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultTest {

    @Test
    void titlesBecomeNamesEverySystemAccepts() {
        assertEquals("¿Qué es O(n)", Vault.fileName("¿Qué es O(n)?"));
        assertEquals("a b c d", Vault.fileName("a/b:c*d"));
        assertEquals("net notes", Vault.fileName(".net notes"));
        assertEquals("Chapter 1", Vault.fileName("  Chapter 1.  "));
        assertEquals("_CON", Vault.fileName("CON"));
        assertEquals("_nul.txt", Vault.fileName("nul.txt"));
        assertEquals("Console", Vault.fileName("Console"));
        assertEquals("Untitled", Vault.fileName(" ?? "));
        assertEquals(80, Vault.fileName("x".repeat(200)).length());
    }

    @Test
    void listsNotebooksAndNotesInNaturalOrder(@TempDir Path home) throws Exception {
        Vault vault = new Vault(home);
        Path algorithms = vault.createNotebook("Algorithms", NotebookType.GENERAL);
        vault.createNotebook("Calculus II", NotebookType.CLASS_NOTES);
        Files.createDirectory(home.resolve(".obsidian"));   // hidden: not a notebook
        Vault.createNote(algorithms, "Lecture 10", "");
        Vault.createNote(algorithms, "Lecture 2", "");
        Path again = Vault.createNote(algorithms, "Lecture 2", "");
        Files.writeString(algorithms.resolve("picture.png"), "not a note");

        assertEquals("Lecture 2 2.md", again.getFileName().toString());
        assertEquals(List.of("Algorithms", "Calculus II"), vault.notebooks().stream().map(Vault.Notebook::name).toList());
        assertEquals(3, vault.notebooks().get(0).notes());
        assertEquals(List.of("Lecture 2", "Lecture 2 2", "Lecture 10"), Vault.notes(algorithms).stream().map(Vault::title).toList());
    }

    @Test
    void aNotebookKeepsItsTypeAndAnythingElseInItsJson(@TempDir Path home) throws Exception {
        Vault vault = new Vault(home);
        Path cp = vault.createNotebook("Competitive", NotebookType.COMPETITIVE_PROGRAMMING);
        assertEquals(NotebookType.COMPETITIVE_PROGRAMMING, Vault.type(cp));

        Path old = Files.createDirectory(home.resolve("Made by hand"));   // no .notebook.json
        assertEquals(NotebookType.GENERAL, Vault.type(old));

        Files.writeString(cp.resolve(".notebook.json"), "{\"type\": \"competitive-programming\", \"color\": \"#4F46E5\"}");
        Vault.setType(cp, NotebookType.CLASS_NOTES);
        assertEquals(NotebookType.CLASS_NOTES, Vault.type(cp));
        assertTrue(Files.readString(cp.resolve(".notebook.json")).contains("\"color\": \"#4F46E5\""));
    }

    @Test
    void writesWholeNotesAndReadsThemBack(@TempDir Path home) throws Exception {
        Path note = Vault.createNote(home, "Árboles AVL", "");
        Vault.write(note, "# Árboles\n\nRotación simple ✓\n");
        assertEquals("# Árboles\n\nRotación simple ✓\n", Vault.read(note));
        try (Stream<Path> files = Files.list(home)) {
            assertEquals(List.of(note), files.toList());   // no temporary file left behind
        }
        Files.write(note, ((char) 0xFEFF + "con BOM").getBytes(StandardCharsets.UTF_8));
        assertEquals("con BOM", Vault.read(note));
    }

    @Test
    void renamesKeepingTheTextAndNeverOverwrite(@TempDir Path home) throws Exception {
        Path note = Vault.createNote(home, "draft", "");
        Vault.write(note, "text");
        Path other = Vault.createNote(home, "Other", "");

        Path renamed = Vault.rename(note, "Draft");   // only the letter case changes
        assertEquals("text", Vault.read(renamed));
        assertThrows(FileAlreadyExistsException.class, () -> Vault.rename(renamed, "Other"));
        assertEquals(List.of("Draft", "Other"), Vault.notes(home).stream().map(Vault::title).toList());
        assertEquals("", Vault.read(other));
    }
}
