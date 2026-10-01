package com.devavaxp.notes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.devavaxp.notes.Text.t;

/**
 * What a notebook specializes in: the kinds of notes it holds, with their properties and the text a
 * new one starts with, and the views to browse them. A notebook keeps its type in .notebook.json.
 */
record NotebookType(String id, String name, String description, List<Kind> kinds, List<View> views) {

    enum Input { TEXT, LIST, CHOICE, DATE, CHECK }

    record Field(String key, String label, Input input, List<String> options) {
        Field(String key, String label, Input input) {
            this(key, label, input, List.of());
        }
    }

    /** A kind of note: its properties, and the text a new one starts with ({today} becomes the date). */
    record Kind(String id, String name, List<Field> fields, String template) {
    }

    /**
     * A way to browse a notebook: its notes of one kind (null: all), only those that match
     * {@code only} ("pinned": true, "status=Solved with help": that value; null: all), sorted by a
     * property ("-" in front: highest first; "title", "modified"), and grouped by another (null: not
     * grouped).
     */
    record View(String name, String kind, String only, String sortBy, String groupBy) {
    }

    /** A short colored mark for a note in the lists and its header: a problem's difficulty. */
    record Badge(String text, String color) {
    }

    private static final Field TAGS = new Field("tags", "Tags", Input.LIST);
    private static final Field PINNED = new Field("pinned", "Pinned", Input.CHECK);
    private static final Field DATE = new Field("date", "Date", Input.DATE);
    private static final Field UNIT = new Field("unit", "Unit", Input.TEXT);
    private static final Field TOPIC = new Field("topic", "Topic", Input.TEXT);
    private static final Field DUE = new Field("due", "Due", Input.DATE);
    private static final Field TASK = new Field("status", "Status", Input.CHOICE, List.of("Pending", "Done"));
    private static final Field TOPICS = new Field("topics", "Topics", Input.LIST);
    private static final Field GRADE = new Field("grade", "Grade", Input.TEXT);
    private static final Field JUDGE = new Field("judge", "Judge", Input.CHOICE, List.of("Codeforces", "AtCoder", "LeetCode", "Other"));
    private static final Field ID = new Field("id", "ID", Input.TEXT);
    private static final Field URL = new Field("url", "Link", Input.TEXT);
    private static final Field DIFFICULTY = new Field("difficulty", "Difficulty", Input.TEXT);
    /** Empty: worked out from the difficulty (Judges.level); set: the note's own word. */
    private static final Field LEVEL = new Field("level", "Level", Input.CHOICE, Judges.LEVELS);
    private static final Field ALGORITHMS = new Field("algorithms", "Algorithms", Input.LIST, Judges.ALGORITHMS);
    private static final Field TECHNIQUES = new Field("techniques", "Techniques", Input.LIST, Judges.TECHNIQUES);
    private static final Field PROBLEM = new Field("status", "Status", Input.CHOICE,
            List.of("To do", "Attempted", "Solved with help", "Solved"));
    /** What a database course covers, Oracle's SQL and PL/SQL; "plsql/cursors" groups under "plsql". */
    private static final Field SQL_TOPICS = new Field("topics", "Topics", Input.LIST, List.of(
            "queries/select", "queries/where", "queries/order by", "queries/functions", "queries/group by",
            "queries/joins", "queries/subqueries", "queries/set operators", "queries/analytic functions",
            "queries/hierarchical", "ddl/tables", "ddl/constraints", "ddl/views", "ddl/sequences", "ddl/indexes",
            "ddl/synonyms", "dml/insert", "dml/update", "dml/delete", "dml/merge", "transactions",
            "security/users", "security/privileges", "security/roles", "plsql/blocks", "plsql/cursors",
            "plsql/exceptions", "plsql/procedures", "plsql/functions", "plsql/packages", "plsql/triggers",
            "design/er model", "design/normalization", "data dictionary"));

    private static final Kind ASSIGNMENT = new Kind("assignment", "Assignment", List.of(DUE, TASK), """
            ---
            kind: assignment
            status: Pending
            ---
            ## Instructions

            ## Notes
            """);

    static final NotebookType GENERAL = new NotebookType("general", "General",
            "Free-form notes, organized with tags.",
            List.of(new Kind("note", "Note", List.of(TAGS, PINNED), "")),
            List.of(new View("All notes", null, null, "-modified", null),
                    new View("Pinned notes", null, "pinned", "-modified", null),
                    new View("By tag", null, null, "title", "tags")));

    static final NotebookType CLASS_NOTES = new NotebookType("class-notes", "Class Notes",
            "One course: its lectures, assignments and exams.",
            List.of(new Kind("lecture", "Lecture", List.of(DATE, UNIT, TOPIC), """
                            ---
                            date: {today}
                            ---
                            ## Key ideas

                            ## Notes

                            ## Questions

                            ## Summary
                            """),
                    ASSIGNMENT,
                    new Kind("exam", "Exam", List.of(DATE, TOPICS, GRADE), """
                            ---
                            kind: exam
                            ---
                            ## Topics

                            ## Notes
                            """)),
            List.of(new View("Lectures", "lecture", null, "-date", null),
                    new View("By unit", "lecture", null, "date", "unit"),
                    new View("Upcoming", null, "upcoming", "when", null),
                    new View("Assignments", "assignment", null, "-due", null),
                    new View("Exams", "exam", null, "-date", null),
                    new View("All notes", null, null, "-when", null)));

    static final NotebookType COMPETITIVE_PROGRAMMING = new NotebookType("competitive-programming", "Competitive Programming",
            "Problems you solve, by topic and difficulty, and the code you reuse.",
            List.of(new Kind("problem", "Problem", List.of(URL, JUDGE, ID, DIFFICULTY, LEVEL, PROBLEM, ALGORITHMS, TECHNIQUES, DATE), """
                            ---
                            status: To do
                            date: {today}
                            ---
                            ## Idea

                            ## Complexity

                            ## Solution

                            ```cpp

                            ```

                            ## Mistakes
                            """),
                    new Kind("snippet", "Snippet", List.of(ALGORITHMS, TECHNIQUES), """
                            ---
                            kind: snippet
                            ---
                            ## When to use

                            ## Complexity

                            ## Code

                            ```cpp

                            ```
                            """)),
            List.of(new View("All problems", "problem", null, "-date", null),
                    new View("To review", "problem", "status=Solved with help", "date", null),
                    new View("By algorithm", "problem", null, "title", "algorithms"),
                    new View("By technique", "problem", null, "title", "techniques"),
                    new View("By difficulty", "problem", null, "title", "level"),
                    new View("By judge", "problem", null, "title", "judge"),
                    new View("By status", "problem", null, "title", "status"),
                    new View("Code Library", "snippet", null, "title", null)));

    static final NotebookType DATABASES = new NotebookType("databases", "Databases",
            "A database course: lectures, SQL and PL/SQL exercises, tables and exams.",
            List.of(new Kind("lecture", "Lecture", List.of(DATE, UNIT, SQL_TOPICS), """
                            ---
                            date: {today}
                            ---
                            ## Key ideas

                            ## Notes

                            ## Examples

                            ```plsql

                            ```

                            ## Questions
                            """),
                    new Kind("exercise", "Exercise", List.of(SQL_TOPICS, LEVEL, PROBLEM, DATE), """
                            ---
                            kind: exercise
                            status: To do
                            date: {today}
                            ---
                            ## Statement

                            ## Query

                            ```plsql

                            ```

                            ## Result

                            ## Notes
                            """),
                    new Kind("table", "Table", List.of(), """
                            ---
                            kind: table
                            ---
                            ## Columns

                            | Column | Type | Constraints |
                            | --- | --- | --- |
                            |  |  |  |

                            ## Create

                            ```plsql

                            ```

                            ## Notes
                            """),
                    new Kind("snippet", "Snippet", List.of(SQL_TOPICS), """
                            ---
                            kind: snippet
                            ---
                            ## When to use

                            ## Code

                            ```plsql

                            ```
                            """),
                    ASSIGNMENT,
                    new Kind("exam", "Exam", List.of(DATE, SQL_TOPICS, GRADE), """
                            ---
                            kind: exam
                            ---
                            ## Topics

                            ## Notes
                            """)),
            List.of(new View("Lectures", "lecture", null, "-date", null),
                    new View("By unit", "lecture", null, "date", "unit"),
                    new View("Exercises", "exercise", null, "-date", null),
                    new View("To review", "exercise", "status=Solved with help", "date", null),
                    new View("By topic", null, null, "title", "topics"),
                    new View("Tables", "table", null, "title", null),
                    new View("Code Library", "snippet", null, "title", null),
                    new View("Upcoming", null, "upcoming", "when", null),
                    new View("Assignments", "assignment", null, "-due", null),
                    new View("Exams", "exam", null, "-date", null),
                    new View("All notes", null, null, "-when", null)));

    static final List<NotebookType> ALL = List.of(GENERAL, CLASS_NOTES, COMPETITIVE_PROGRAMMING, DATABASES);

    static NotebookType byId(String id) {
        for (NotebookType t : ALL) if (t.id.equals(id)) return t;
        return GENERAL;
    }

    /** A course (Class Notes, Databases): its assignments and exams go to Upcoming, here and on Home. */
    boolean course() {
        return kinds.contains(ASSIGNMENT);
    }

    /** A note's kind: its "kind" property, or else the first kind. */
    Kind kindOf(Note note) {
        String id = note.get("kind");
        for (Kind k : kinds) if (k.id().equals(id)) return k;
        return kinds.get(0);
    }

    /**
     * A note's values for a property, some worked out rather than written: a problem's "level"
     * (from its difficulty, unless the note sets it), and in a course "when" (an assignment's due
     * date, another note's date) and "upcoming" (true while it is still to do).
     */
    List<String> values(Note note, String key) {
        if (this == COMPETITIVE_PROGRAMMING && key.equals("level")) {
            String level = Judges.level(note);
            return level.isEmpty() ? List.of() : List.of(level);
        }
        if (course() && key.equals("when")) {
            LocalDate when = Agenda.when(note);
            return when == null ? List.of() : List.of(when.toString());
        }
        if (course() && key.equals("upcoming")) {
            return Agenda.upcoming(note, LocalDate.now()) ? List.of("true") : List.of();
        }
        if (key.equals("tags")) {   // the tags property and the #tags written in the text
            List<String> tags = new ArrayList<>(note.list(key));
            note.inlineTags().stream().filter(t -> tags.stream().noneMatch(t::equalsIgnoreCase)).forEach(tags::add);
            return tags;
        }
        return note.list(key);
    }

    /** Whether the note is one a view's {@code only} asks for: "pinned" (true) or "status=Solved with help". */
    boolean matches(Note note, String only) {
        int eq = only.indexOf('=');
        List<String> values = values(note, eq < 0 ? only : only.substring(0, eq));
        return eq < 0 ? values.contains("true") : values.stream().anyMatch(only.substring(eq + 1)::equalsIgnoreCase);
    }

    /**
     * A short colored mark: how near a course's assignment or exam is, a problem's difficulty as its
     * judge colors it, an exercise's level.
     */
    Badge badge(Note note) {
        String kind = kindOf(note).id();
        if (course() && (kind.equals("assignment") || kind.equals("exam"))) return Agenda.badge(note, LocalDate.now());
        if (kind.equals("exercise")) {
            String level = note.get("level");
            return level.isEmpty() ? null : new Badge(t(level), Judges.levelColor(level));
        }
        if (this != COMPETITIVE_PROGRAMMING || !kind.equals("problem")) return null;
        String text = note.get("difficulty").isEmpty() ? Judges.level(note) : note.get("difficulty");
        return text.isEmpty() ? null : new Badge(t(text), Judges.color(note));
    }

    /** The properties of a view's notes: those of its kind, or of every kind once. */
    List<Field> fields(View view) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Kind k : kinds) {
            if (view.kind() == null || view.kind().equals(k.id())) k.fields().forEach(f -> fields.putIfAbsent(f.key(), f));
        }
        return List.copyOf(fields.values());
    }
}
