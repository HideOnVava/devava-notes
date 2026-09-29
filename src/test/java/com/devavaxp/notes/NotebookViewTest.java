package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.View;
import com.devavaxp.notes.NotebookView.Entry;
import com.devavaxp.notes.NotebookView.Group;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotebookViewTest {

    private static final NotebookType CP = NotebookType.COMPETITIVE_PROGRAMMING;

    @Test
    void viewsGroupFilterAndSortTheNotesOfTheirKind() {
        List<Entry> notes = List.of(
                entry("Two Sum", "algorithms: [hash map]\nstatus: Solved\ndate: 2026-09-20"),
                entry("Watermelon", "algorithms: [math, brute force]\nstatus: Solved\ndate: 2026-09-28"),
                entry("Árbol", "status: To do"),
                entry("DSU", "kind: snippet\nalgorithms: [dsu]"));

        List<Group> byAlgorithm = NotebookView.rows(CP, view("By algorithm"), null, "", notes);
        assertEquals(List.of("brute force", "hash map", "math", ""), byAlgorithm.stream().map(Group::value).toList());
        assertEquals(List.of("Árbol"), titles(byAlgorithm.get(3)));   // no algorithm yet: last
        assertEquals(List.of("Watermelon"), titles(NotebookView.rows(CP, view("By algorithm"), "Math", "", notes).get(0)));
        assertEquals(List.of("To do", "Solved"),   // in the order of the choices
                NotebookView.rows(CP, view("By status"), null, "", notes).stream().map(Group::value).toList());
        assertEquals(List.of("Watermelon", "Two Sum", "Árbol"), titles(NotebookView.rows(CP, view("All problems"), null, "", notes).get(0)));
        assertEquals(List.of("Árbol"), titles(NotebookView.rows(CP, view("All problems"), null, "arbol", notes).get(0)));
        assertEquals(List.of("DSU"), titles(NotebookView.rows(CP, view("Code Library"), null, "", notes).get(0)));
    }

    @Test
    void everyTemplateMakesANoteOfItsOwnKind() {
        for (NotebookType type : NotebookType.ALL) {
            for (NotebookType.Kind kind : type.kinds()) {
                assertEquals(kind, type.kindOf(Note.parse(kind.template().replace("{today}", "2026-09-28"))), type.name() + ": " + kind.name());
            }
        }
    }

    private static Entry entry(String title, String properties) {
        return new Entry(Path.of(title + ".md"), Note.parse("---\n" + properties + "\n---\n"), FileTime.fromMillis(0));
    }

    private static View view(String name) {
        return CP.views().stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<String> titles(Group group) {
        return group.notes().stream().map(Entry::title).toList();
    }
}
