package com.devavaxp.notes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    static final NotebookType GENERAL = new NotebookType("general", "General",
            "Free-form notes, organized with tags.",
            List.of(new Kind("note", "Note", List.of(TAGS, PINNED), "")),
            List.of(new View("All notes", null, null, "-modified", null),
                    new View("Pinned", null, "pinned", "-modified", null),
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
                    new Kind("assignment", "Assignment", List.of(DUE, TASK), """
                            ---
                            kind: assignment
                            status: Pending
                            ---
                            ## Instructions

                            ## Notes
                            """),
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

    static final List<NotebookType> ALL = List.of(GENERAL, CLASS_NOTES, COMPETITIVE_PROGRAMMING);

    static NotebookType byId(String id) {
        for (NotebookType t : ALL) if (t.id.equals(id)) return t;
        return GENERAL;
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
        if (this == CLASS_NOTES && key.equals("when")) {
            LocalDate when = Agenda.when(note);
            return when == null ? List.of() : List.of(when.toString());
        }
        if (this == CLASS_NOTES && key.equals("upcoming")) {
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

    /** A short colored mark: a problem's difficulty as its judge colors it, how near a course's assignment or exam is. */
    Badge badge(Note note) {
        if (this == CLASS_NOTES) return Agenda.badge(note, LocalDate.now());
        if (this != COMPETITIVE_PROGRAMMING || !kindOf(note).id().equals("problem")) return null;
        String text = note.get("difficulty").isEmpty() ? Judges.level(note) : note.get("difficulty");
        return text.isEmpty() ? null : new Badge(text, Judges.color(note));
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
