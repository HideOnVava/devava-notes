package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.View;
import com.devavaxp.notes.NotebookView.Entry;
import com.devavaxp.notes.NotebookView.Group;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
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
    void subtopicsLiveUnderTheirTopicAndLevelsJoinEveryJudge() {
        List<Entry> notes = List.of(
                entry("Dijkstra", "algorithms: [graphs/dijkstra]\njudge: Codeforces\ndifficulty: 1700"),
                entry("BFS", "algorithms: [graphs/bfs, greedy]\njudge: LeetCode\ndifficulty: Medium"),
                entry("Plain graph", "algorithms: [graphs]\njudge: AtCoder\nid: abc300_b\nstatus: Solved with help"));

        assertEquals(List.of("graphs", "greedy"), NotebookView.rows(CP, view("By algorithm"), null, "", notes).stream().map(Group::value).toList());
        assertEquals(List.of("graphs", "graphs/bfs", "graphs/dijkstra", "greedy"),
                List.copyOf(NotebookView.counts(CP, view("By algorithm"), notes).keySet()));
        assertEquals(3, NotebookView.counts(CP, view("By algorithm"), notes).get("graphs"));
        assertEquals(List.of("Dijkstra"), titles(NotebookView.rows(CP, view("By algorithm"), "graphs/dijkstra", "", notes).get(0)));
        assertEquals(3, NotebookView.rows(CP, view("By algorithm"), "Graphs", "", notes).get(0).notes().size());
        assertEquals(List.of("Easy", "Medium", "Hard"),   // AtCoder's B, LeetCode's word, Codeforces' 1700
                NotebookView.rows(CP, view("By difficulty"), null, "", notes).stream().map(Group::value).toList());
        assertEquals(List.of("Plain graph"), titles(NotebookView.rows(CP, view("To review"), null, "", notes).get(0)));
    }

    @Test
    void aCourseShowsWhatIsStillToDoLateFirst() {
        NotebookType course = NotebookType.CLASS_NOTES;
        LocalDate today = LocalDate.now();
        List<Entry> notes = List.of(
                entry("Homework 3", "kind: assignment\ndue: " + today.plusDays(1)),
                entry("Homework 2", "kind: assignment\ndue: " + today.minusDays(3) + "\nstatus: Pending"),
                entry("Homework 1", "kind: assignment\ndue: " + today.minusDays(9) + "\nstatus: Done"),
                entry("Midterm", "kind: exam\ndate: " + today.plusDays(5)),
                entry("Quiz", "kind: exam\ndate: " + today.minusDays(1)),
                entry("Limits", "date: " + today));
        assertEquals(List.of("Homework 2", "Homework 3", "Midterm"), titles(NotebookView.rows(course, view(course, "Upcoming"), null, "", notes).get(0)));
        assertEquals(List.of("Limits"), titles(NotebookView.rows(course, view(course, "Lectures"), null, "", notes).get(0)));
        assertEquals("3 days late", course.badge(notes.get(1).note()).text());
    }

    @Test
    void aDatabasesNotebookIsACourseWithItsSqlByTopic() {
        NotebookType db = NotebookType.DATABASES;
        LocalDate today = LocalDate.now();
        List<Entry> notes = List.of(
                entry("Joins", "date: 2026-09-01\nunit: 3. DML\ntopics: [queries/joins]"),
                entry("Employees per department", "kind: exercise\ntopics: [queries/joins, queries/group by]\nlevel: Medium\nstatus: Solved with help"),
                entry("Explicit cursor", "kind: snippet\ntopics: [plsql/cursors]"),
                entry("EMPLOYEES", "kind: table"),
                entry("Second exam", "kind: exam\ndate: " + today.plusDays(2) + "\ntopics: [plsql]"));

        assertEquals(List.of("plsql", "queries"), NotebookView.rows(db, view(db, "By topic"), null, "", notes).stream()
                .map(Group::value).filter(v -> !v.isEmpty()).toList());
        assertEquals(List.of("plsql", "plsql/cursors", "queries", "queries/group by", "queries/joins", ""),   // "": No topics
                List.copyOf(NotebookView.counts(db, view(db, "By topic"), notes).keySet()));
        assertEquals(List.of("Employees per department", "Joins"), titles(NotebookView.rows(db, view(db, "By topic"), "queries/joins", "", notes).get(0)));
        assertEquals(List.of("Employees per department"), titles(NotebookView.rows(db, view(db, "To review"), null, "", notes).get(0)));
        assertEquals(List.of("EMPLOYEES"), titles(NotebookView.rows(db, view(db, "Tables"), null, "", notes).get(0)));
        assertEquals(List.of("Second exam"), titles(NotebookView.rows(db, view(db, "Upcoming"), null, "", notes).get(0)));
        assertEquals("In 2 days", db.badge(notes.get(4).note()).text());
        assertEquals("Medium", db.badge(notes.get(1).note()).text());
    }

    @Test
    void tagsWrittenInTheTextJoinTheTagsProperty() {
        NotebookType general = NotebookType.GENERAL;
        List<Entry> notes = List.of(
                new Entry(Path.of("Plans.md"), Note.parse("---\ntags: [ideas]\n---\nReading #books"), FileTime.fromMillis(0)),
                new Entry(Path.of("Novel.md"), Note.parse("About #Books"), FileTime.fromMillis(0)));
        assertEquals(List.of("Books", "ideas"), NotebookView.rows(general, view(general, "By tag"), null, "", notes).stream().map(Group::value).toList());
        assertEquals(2, NotebookView.rows(general, view(general, "By tag"), "books", "", notes).get(0).notes().size());
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
        return view(CP, name);
    }

    private static View view(NotebookType type, String name) {
        return type.views().stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<String> titles(Group group) {
        return group.notes().stream().map(Entry::title).toList();
    }
}
