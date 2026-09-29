package com.devavaxp.notes;

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
     * A way to browse a notebook: its notes of one kind (null: all), only those whose {@code only}
     * property is true (null: all), sorted by a property ("-" in front: highest first; "title",
     * "modified"), and grouped by another (null: not grouped).
     */
    record View(String name, String kind, String only, String sortBy, String groupBy) {
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
    private static final Field ALGORITHMS = new Field("algorithms", "Algorithms", Input.LIST);
    private static final Field TECHNIQUES = new Field("techniques", "Techniques", Input.LIST);
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
            List.of(new View("All notes", null, null, "-date", null),
                    new View("Lectures", "lecture", null, "-date", null),
                    new View("By unit", "lecture", null, "date", "unit"),
                    new View("Assignments", "assignment", null, "due", null),
                    new View("Exams", "exam", null, "date", null)));

    static final NotebookType COMPETITIVE_PROGRAMMING = new NotebookType("competitive-programming", "Competitive Programming",
            "Problems you solve, by topic and difficulty, and the code you reuse.",
            List.of(new Kind("problem", "Problem", List.of(JUDGE, ID, URL, DIFFICULTY, ALGORITHMS, TECHNIQUES, PROBLEM, DATE), """
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
                    new View("By algorithm", "problem", null, "title", "algorithms"),
                    new View("By technique", "problem", null, "title", "techniques"),
                    new View("By difficulty", "problem", null, "title", "difficulty"),
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

    /** The properties of a view's notes: those of its kind, or of every kind once. */
    List<Field> fields(View view) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Kind k : kinds) {
            if (view.kind() == null || view.kind().equals(k.id())) k.fields().forEach(f -> fields.putIfAbsent(f.key(), f));
        }
        return List.copyOf(fields.values());
    }
}
