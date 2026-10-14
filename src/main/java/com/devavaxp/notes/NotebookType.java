package com.devavaxp.notes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a notebook specializes in: the kinds of notes it holds, with their properties and the text a
 * new one starts with, and the views to browse them. A notebook keeps its type in .notebook.json.
 * What a note keeps is in English ("status: Solved"), as notes of earlier versions have it; the app
 * shows it in Spanish (Text.shown).
 */
record NotebookType(String id, String name, String description, List<Kind> kinds, List<View> views) {

    enum Input { TEXT, LIST, CHOICE, DATE, CHECK }

    record Field(String key, String label, Input input, List<String> options) {
        Field(String key, String label, Input input) {
            this(key, label, input, List.of());
        }
    }

    /**
     * A kind of note: its name, what makes one ("Nueva clase") and what it is for, its properties,
     * and the text a new one starts with ({today} becomes the date).
     */
    record Kind(String id, String name, String create, String about, List<Field> fields, String template) {
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

    private static final Field TAGS = new Field("tags", "Etiquetas", Input.LIST);
    private static final Field PINNED = new Field("pinned", "Fijada", Input.CHECK);
    private static final Field DATE = new Field("date", "Fecha", Input.DATE);
    private static final Field UNIT = new Field("unit", "Unidad", Input.TEXT);
    private static final Field TOPIC = new Field("topic", "Tema", Input.TEXT);
    private static final Field DUE = new Field("due", "Entrega", Input.DATE);
    private static final Field TASK = new Field("status", "Estado", Input.CHOICE, List.of("Pending", "Done"));
    private static final Field TOPICS = new Field("topics", "Temas", Input.LIST);
    private static final Field GRADE = new Field("grade", "Calificación", Input.TEXT);
    private static final Field JUDGE = new Field("judge", "Juez", Input.CHOICE, List.of("Codeforces", "AtCoder", "LeetCode", "Other"));
    private static final Field ID = new Field("id", "ID", Input.TEXT);
    private static final Field URL = new Field("url", "Enlace", Input.TEXT);
    private static final Field DIFFICULTY = new Field("difficulty", "Dificultad", Input.TEXT);
    /** Empty: worked out from the difficulty (Judges.level); set: the note's own word. */
    private static final Field LEVEL = new Field("level", "Nivel", Input.CHOICE, Judges.LEVELS);
    private static final Field ALGORITHMS = new Field("algorithms", "Algoritmos", Input.LIST, Judges.ALGORITHMS);
    private static final Field TECHNIQUES = new Field("techniques", "Técnicas", Input.LIST, Judges.TECHNIQUES);
    private static final Field PROBLEM = new Field("status", "Estado", Input.CHOICE,
            List.of("To do", "Attempted", "Solved with help", "Solved"));
    /** What a database course covers, Oracle's SQL and PL/SQL; "plsql/cursores" groups under "plsql". */
    private static final Field SQL_TOPICS = new Field("topics", "Temas", Input.LIST, List.of(
            "consultas/select", "consultas/where", "consultas/order by", "consultas/funciones", "consultas/group by",
            "consultas/joins", "consultas/subconsultas", "consultas/operadores de conjuntos", "consultas/funciones analíticas",
            "consultas/jerárquicas", "ddl/tablas", "ddl/restricciones", "ddl/vistas", "ddl/secuencias", "ddl/índices",
            "ddl/sinónimos", "dml/insert", "dml/update", "dml/delete", "dml/merge", "transacciones",
            "seguridad/usuarios", "seguridad/privilegios", "seguridad/roles", "plsql/bloques", "plsql/cursores",
            "plsql/excepciones", "plsql/procedimientos", "plsql/funciones", "plsql/paquetes", "plsql/disparadores",
            "diseño/modelo entidad-relación", "diseño/normalización", "diccionario de datos"));

    private static final Kind ASSIGNMENT = new Kind("assignment", "Tarea", "Nueva tarea", "Con fecha de entrega", List.of(DUE, TASK), """
            ---
            kind: assignment
            status: Pending
            ---
            ## Instrucciones

            ## Notas
            """);

    static final NotebookType GENERAL = new NotebookType("general", "General",
            "Notas libres, organizadas con etiquetas.",
            List.of(new Kind("note", "Nota", "Nueva nota", "Una nota libre", List.of(TAGS, PINNED), "")),
            List.of(new View("Todas las notas", null, null, "-modified", null),
                    new View("Notas fijadas", null, "pinned", "-modified", null),
                    new View("Por etiqueta", null, null, "title", "tags")));

    static final NotebookType CLASS_NOTES = new NotebookType("class-notes", "Apuntes de clase",
            "Una materia: sus clases, tareas y exámenes.",
            List.of(new Kind("lecture", "Clase", "Nueva clase", "Lo que se vio en una clase", List.of(DATE, UNIT, TOPIC), """
                            ---
                            date: {today}
                            ---
                            ## Ideas clave

                            ## Notas

                            ## Preguntas

                            ## Resumen
                            """),
                    ASSIGNMENT,
                    new Kind("exam", "Examen", "Nuevo examen", "Con fecha, temas y calificación", List.of(DATE, TOPICS, GRADE), """
                            ---
                            kind: exam
                            ---
                            ## Temas

                            ## Notas
                            """)),
            List.of(new View("Clases", "lecture", null, "-date", null),
                    new View("Por unidad", "lecture", null, "date", "unit"),
                    new View("Lo que se acerca", null, "upcoming", "when", null),
                    new View("Tareas", "assignment", null, "-due", null),
                    new View("Exámenes", "exam", null, "-date", null),
                    new View("Todas las notas", null, null, "-when", null)));

    static final NotebookType COMPETITIVE_PROGRAMMING = new NotebookType("competitive-programming", "Programación competitiva",
            "Los problemas que resuelves, por tema y dificultad, y el código que reutilizas.",
            List.of(new Kind("problem", "Problema", "Nuevo problema", "De Codeforces, AtCoder o LeetCode",
                            List.of(URL, JUDGE, ID, DIFFICULTY, LEVEL, PROBLEM, ALGORITHMS, TECHNIQUES, DATE), """
                            ---
                            status: To do
                            date: {today}
                            ---
                            ## Idea

                            ## Complejidad

                            ## Solución

                            ```cpp

                            ```

                            ## Errores
                            """),
                    new Kind("snippet", "Fragmento", "Nuevo fragmento", "Código para reutilizar", List.of(ALGORITHMS, TECHNIQUES), """
                            ---
                            kind: snippet
                            ---
                            ## Cuándo usarlo

                            ## Complejidad

                            ## Código

                            ```cpp

                            ```
                            """)),
            List.of(new View("Todos los problemas", "problem", null, "-date", null),
                    new View("Por repasar", "problem", "status=Solved with help", "date", null),
                    new View("Por algoritmo", "problem", null, "title", "algorithms"),
                    new View("Por técnica", "problem", null, "title", "techniques"),
                    new View("Por dificultad", "problem", null, "title", "level"),
                    new View("Por juez", "problem", null, "title", "judge"),
                    new View("Por estado", "problem", null, "title", "status"),
                    new View("Biblioteca de código", "snippet", null, "title", null)));

    static final NotebookType DATABASES = new NotebookType("databases", "Bases de datos",
            "Una materia de bases de datos: clases, ejercicios de SQL y PL/SQL, tablas y exámenes.",
            List.of(new Kind("lecture", "Clase", "Nueva clase", "Lo que se vio en una clase", List.of(DATE, UNIT, SQL_TOPICS), """
                            ---
                            date: {today}
                            ---
                            ## Ideas clave

                            ## Notas

                            ## Ejemplos

                            ```plsql

                            ```

                            ## Preguntas
                            """),
                    new Kind("exercise", "Ejercicio", "Nuevo ejercicio", "Un ejercicio de SQL con su consulta",
                            List.of(SQL_TOPICS, LEVEL, PROBLEM, DATE), """
                            ---
                            kind: exercise
                            status: To do
                            date: {today}
                            ---
                            ## Enunciado

                            ## Consulta

                            ```plsql

                            ```

                            ## Resultado

                            ## Notas
                            """),
                    new Kind("table", "Tabla", "Nueva tabla", "Una tabla del esquema y su CREATE TABLE", List.of(), """
                            ---
                            kind: table
                            ---
                            ## Columnas

                            | Columna | Tipo | Restricciones |
                            | --- | --- | --- |
                            |  |  |  |

                            ## Creación

                            ```plsql

                            ```

                            ## Notas
                            """),
                    new Kind("snippet", "Fragmento", "Nuevo fragmento", "Una consulta para reutilizar", List.of(SQL_TOPICS), """
                            ---
                            kind: snippet
                            ---
                            ## Cuándo usarlo

                            ## Código

                            ```plsql

                            ```
                            """),
                    ASSIGNMENT,
                    new Kind("exam", "Examen", "Nuevo examen", "Con fecha, temas y calificación", List.of(DATE, SQL_TOPICS, GRADE), """
                            ---
                            kind: exam
                            ---
                            ## Temas

                            ## Notas
                            """)),
            List.of(new View("Clases", "lecture", null, "-date", null),
                    new View("Por unidad", "lecture", null, "date", "unit"),
                    new View("Ejercicios", "exercise", null, "-date", null),
                    new View("Por repasar", "exercise", "status=Solved with help", "date", null),
                    new View("Por tema", null, null, "title", "topics"),
                    new View("Tablas", "table", null, "title", null),
                    new View("Biblioteca de código", "snippet", null, "title", null),
                    new View("Lo que se acerca", null, "upcoming", "when", null),
                    new View("Tareas", "assignment", null, "-due", null),
                    new View("Exámenes", "exam", null, "-date", null),
                    new View("Todas las notas", null, null, "-when", null)));

    static final List<NotebookType> ALL = List.of(GENERAL, CLASS_NOTES, COMPETITIVE_PROGRAMMING, DATABASES);

    static NotebookType byId(String id) {
        for (NotebookType t : ALL) if (t.id.equals(id)) return t;
        return GENERAL;
    }

    /** A course (Class Notes, Databases): its assignments and exams go to "Lo que se acerca", here and on Home. */
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
            return level.isEmpty() ? null : new Badge(Text.shown(level), Judges.levelColor(level));
        }
        if (this != COMPETITIVE_PROGRAMMING || !kind.equals("problem")) return null;
        String text = note.get("difficulty").isEmpty() ? Judges.level(note) : note.get("difficulty");
        return text.isEmpty() ? null : new Badge(Text.shown(text), Judges.color(note));
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
