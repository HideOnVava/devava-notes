package com.devavaxp.notes;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Map.entry;

/**
 * The app's words, in English or in Spanish. The code writes them in English and shows
 * {@code t(text)}, the Spanish text when the app speaks Spanish: Windows' language, or the one
 * picked in ⋯ → Language. What the notes keep stays English ("status: Solved"), so a notebook
 * works the same in either language; only the headings of a new note come in the app's.
 */
final class Text {

    static final String ENGLISH = "en", SPANISH = "es";
    private static Locale locale = Locale.ENGLISH;

    private Text() {
    }

    /** "es" for Spanish; anything else is English. */
    static void use(String language) {
        locale = SPANISH.equals(language) ? Locale.forLanguageTag(SPANISH) : Locale.ENGLISH;
    }

    static String language() {
        return locale.getLanguage();
    }

    static Locale locale() {
        return locale;
    }

    static String t(String english) {
        return locale.getLanguage().equals(SPANISH) ? ES.getOrDefault(english, english) : english;
    }

    /** {@link #t(String)} with String.format's %s and %d filled in. */
    static String t(String english, Object... args) {
        return String.format(locale, t(english), args);
    }

    /** A new note's text: the lines of its template that are words (its headings) in the app's language. */
    static String template(String text) {
        return text.lines().map(Text::t).collect(Collectors.joining("\n", "", text.endsWith("\n") ? "\n" : ""));
    }

    /** Whether the Spanish words have this text, for the tests. */
    static boolean translated(String english) {
        return ES.containsKey(english);
    }

    static Map<String, String> spanish() {
        return ES;
    }

    private static final Map<String, String> ES = Map.ofEntries(
            // Home and the bar
            entry("+ New notebook", "+ Nuevo cuaderno"),
            entry("Search in all notes…", "Buscar en todas las notas…"),
            entry("Import notebook…", "Importar cuaderno…"),
            entry("Open data folder", "Abrir la carpeta de datos"),
            entry("Quick open…", "Abrir rápido…"),
            entry("Open any note by its title (Ctrl+O); search all their text with Ctrl+Shift+F",
                    "Abre cualquier nota por su título (Ctrl+O); busca en todo su texto con Ctrl+Shift+F"),
            entry("Dark theme", "Tema oscuro"),
            entry("Language", "Idioma"),
            entry("Couldn't save the language", "No se pudo guardar el idioma"),
            entry("More", "Más"),
            entry("Couldn't open your notes", "No se pudieron abrir tus notas"),
            entry("Welcome to Devava Notes", "Te damos la bienvenida a Devava Notes"),
            entry("A notebook keeps the notes of one subject, course or project.",
                    "Un cuaderno guarda las notas de una materia, un curso o un proyecto."),
            entry("Notebooks", "Cuadernos"),
            entry("UPCOMING", "PRÓXIMOS"),
            entry("RECENT", "RECIENTES"),
            entry("%s · edited %s", "%s · editado el %s"),
            entry("no notes", "sin notas"),
            entry("1 note", "1 nota"),
            entry("%d notes", "%d notas"),
            entry("Just now", "Ahora mismo"),
            entry("1 minute ago", "Hace 1 minuto"),
            entry("%d minutes ago", "Hace %d minutos"),
            entry("1 hour ago", "Hace 1 hora"),
            entry("%d hours ago", "Hace %d horas"),
            entry("Yesterday", "Ayer"),
            entry("MMM d, yyyy", "d MMM yyyy"),
            entry("MMM d", "d MMM"),

            // A notebook's menu and its dialogs
            entry("Rename…", "Cambiar nombre…"),
            entry("Change type…", "Cambiar tipo…"),
            entry("C++ template…", "Plantilla de C++…"),
            entry("Export as ZIP…", "Exportar como ZIP…"),
            entry("Show in folder", "Mostrar en la carpeta"),
            entry("Move to trash…", "Mover a la papelera…"),
            entry("Export “%s”", "Exportar “%s”"),
            entry("Import a notebook", "Importar un cuaderno"),
            entry("Couldn't export the notebook", "No se pudo exportar el cuaderno"),
            entry("Couldn't import the notebook", "No se pudo importar el cuaderno"),
            entry("New problems and + Solution start with it (LeetCode problems start empty).",
                    "Los problemas nuevos y + Solución empiezan con ella (los de LeetCode empiezan vacíos)."),
            entry("C++ template", "Plantilla de C++"),
            entry("Save", "Guardar"),
            entry("Couldn't save the template", "No se pudo guardar la plantilla"),
            entry("New notebook", "Nuevo cuaderno"),
            entry("Couldn't create the notebook", "No se pudo crear el cuaderno"),
            entry("Rename notebook", "Cambiar nombre del cuaderno"),
            entry("Name", "Nombre"),
            entry("Couldn't rename the notebook", "No se pudo cambiar el nombre del cuaderno"),
            entry("Change “%s” to", "Cambiar “%s” a"),
            entry("Couldn't change the type", "No se pudo cambiar el tipo"),
            entry("Move “%s” to the trash?", "¿Mover “%s” a la papelera?"),
            entry("The notebook goes to the trash, where you can restore it.",
                    "El cuaderno va a la papelera, donde puedes restaurarlo."),
            entry("The notebook and its %s go to the trash, where you can restore them.",
                    "El cuaderno y sus %s van a la papelera, donde puedes restaurarlos."),
            entry("Move to trash", "Mover a la papelera"),
            entry("Couldn't move “%s” to the trash", "No se pudo mover “%s” a la papelera"),
            entry("Create", "Crear"),
            entry("Change", "Cambiar"),

            // Inside a notebook
            entry("‹ Home", "‹ Inicio"),
            entry("+ New  ▾", "+ Nuevo  ▾"),
            entry("+ New note", "+ Nueva nota"),
            entry("Couldn't list the notes", "No se pudieron listar las notas"),
            entry("Couldn't open “%s”", "No se pudo abrir “%s”"),
            entry("+ Solution", "+ Solución"),
            entry("+ Query", "+ Consulta"),
            entry("Insert snippet…", "Insertar fragmento…"),
            entry("No note open", "Ninguna nota abierta"),
            entry("Pick a note, or press Ctrl+N to write a new one.", "Elige una nota o pulsa Ctrl+N para escribir una nueva."),
            entry("New note", "Nueva nota"),
            entry("New lecture", "Nueva clase"),
            entry("New assignment", "Nueva tarea"),
            entry("New exam", "Nuevo examen"),
            entry("New problem", "Nuevo problema"),
            entry("New snippet", "Nuevo fragmento"),
            entry("New exercise", "Nuevo ejercicio"),
            entry("New table", "Nueva tabla"),
            entry("Title", "Título"),
            entry("Link", "Enlace"),
            entry("https://codeforces.com/…, atcoder.jp/…, leetcode.com/… (optional)",
                    "https://codeforces.com/…, atcoder.jp/…, leetcode.com/… (opcional)"),
            entry("Couldn't create the note", "No se pudo crear la nota"),
            entry("Couldn't create “%s”", "No se pudo crear “%s”"),
            entry("Rename note", "Cambiar nombre de la nota"),
            entry("Couldn't rename the note", "No se pudo cambiar el nombre de la nota"),
            entry("Its latest changes aren't saved: %s", "Sus últimos cambios no están guardados: %s"),
            entry("You can restore it from the trash.", "Puedes restaurarla desde la papelera."),
            entry("### Solution · O( )", "### Solución · O( )"),
            entry("The Code Library is empty", "La biblioteca de código está vacía"),
            entry("Add a snippet with + New ▾ → Snippet, with its code in a block of code.",
                    "Agrega un fragmento con + Nuevo ▾ → Fragmento, con su código en un bloque de código."),
            entry("Filter", "Filtrar"),
            entry("Insert snippet", "Insertar fragmento"),
            entry("Insert", "Insertar"),
            entry("Quick open", "Abrir rápido"),
            entry("Open", "Abrir"),
            entry("Words to find", "Palabras a buscar"),
            entry("Search in all notes", "Buscar en todas las notas"),
            entry("Couldn't add the image", "No se pudo agregar la imagen"),
            entry("Couldn't paste the image", "No se pudo pegar la imagen"),
            entry("Nothing to insert", "Nada que insertar"),
            entry("“%s” has no block of code yet.", "“%s” aún no tiene un bloque de código."),
            entry("Mark as done", "Marcar como hecha"),
            entry("Mark as pending", "Marcar como pendiente"),
            entry("Couldn't change “%s”", "No se pudo cambiar “%s”"),
            entry("Read", "Leer"),
            entry("Edit", "Editar"),
            entry("Read or edit (Ctrl+E)", "Leer o editar (Ctrl+E)"),
            entry("Not saved: %s", "Sin guardar: %s"),
            entry("Leave without saving?", "¿Salir sin guardar?"),
            entry("The latest changes to “%s” couldn't be saved (%s). If you leave, they are lost.",
                    "Los últimos cambios de “%s” no se pudieron guardar (%s). Si sales, se pierden."),
            entry("Leave anyway", "Salir de todos modos"),
            entry("Couldn't open the folder", "No se pudo abrir la carpeta"),
            entry("List", "Lista"),
            entry("Table", "Tabla"),
            entry("No notes here", "No hay notas aquí"),
            entry("No %s", "Sin %s"),
            entry("Open the link", "Abrir el enlace"),
            entry("yyyy-mm-dd", "aaaa-mm-dd"),

            // Why something failed
            entry("there's already one with that name", "ya hay uno con ese nombre"),
            entry("access to the file was denied", "se negó el acceso al archivo"),
            entry("the file is no longer there", "el archivo ya no está"),
            entry("it isn't UTF-8 text", "no es texto UTF-8"),
            entry("this system has no trash to move it to, so nothing was deleted",
                    "este sistema no tiene papelera, así que no se borró nada"),
            entry("it could not be moved to the trash", "no se pudo mover a la papelera"),
            entry("it is not a ZIP file", "no es un archivo ZIP"),
            entry("the ZIP is empty", "el ZIP está vacío"),
            entry("“%s” in it points outside the notebook", "“%s” apunta fuera del cuaderno"),

            // What is coming in a course
            entry("Done", "Hecha"),
            entry("1 day late", "1 día de retraso"),
            entry("%d days late", "%d días de retraso"),
            entry("Due today", "Vence hoy"),
            entry("Due tomorrow", "Vence mañana"),
            entry("Due in %d days", "Vence en %d días"),
            entry("Due %s", "Vence el %s"),
            entry("Past", "Pasado"),
            entry("Today", "Hoy"),
            entry("Tomorrow", "Mañana"),
            entry("In %d days", "En %d días"),
            entry("Grade %s", "Calificación %s"),

            // Notebook types: their names, views, kinds of notes, properties and choices
            entry("Class Notes", "Apuntes de clase"),
            entry("Competitive Programming", "Programación competitiva"),
            entry("Databases", "Bases de datos"),
            entry("Free-form notes, organized with tags.", "Notas libres, organizadas con etiquetas."),
            entry("One course: its lectures, assignments and exams.", "Un curso: sus clases, tareas y exámenes."),
            entry("Problems you solve, by topic and difficulty, and the code you reuse.",
                    "Los problemas que resuelves, por tema y dificultad, y el código que reutilizas."),
            entry("A database course: lectures, SQL and PL/SQL exercises, tables and exams.",
                    "Un curso de bases de datos: clases, ejercicios de SQL y PL/SQL, tablas y exámenes."),
            entry("All notes", "Todas las notas"),
            entry("Pinned notes", "Notas fijadas"),
            entry("By tag", "Por etiqueta"),
            entry("Lectures", "Clases"),
            entry("By unit", "Por unidad"),
            entry("Upcoming", "Próximos"),
            entry("Assignments", "Tareas"),
            entry("Exams", "Exámenes"),
            entry("All problems", "Todos los problemas"),
            entry("To review", "Por repasar"),
            entry("By algorithm", "Por algoritmo"),
            entry("By technique", "Por técnica"),
            entry("By difficulty", "Por dificultad"),
            entry("By judge", "Por juez"),
            entry("By status", "Por estado"),
            entry("Code Library", "Biblioteca de código"),
            entry("Exercises", "Ejercicios"),
            entry("By topic", "Por tema"),
            entry("Tables", "Tablas"),
            entry("Note", "Nota"),
            entry("Lecture", "Clase"),
            entry("Assignment", "Tarea"),
            entry("Exam", "Examen"),
            entry("Problem", "Problema"),
            entry("Snippet", "Fragmento"),
            entry("Exercise", "Ejercicio"),
            entry("Tags", "Etiquetas"),
            entry("Pinned", "Fijada"),
            entry("Date", "Fecha"),
            entry("Unit", "Unidad"),
            entry("Topic", "Tema"),
            entry("Due", "Entrega"),
            entry("Status", "Estado"),
            entry("Topics", "Temas"),
            entry("Grade", "Calificación"),
            entry("Judge", "Juez"),
            entry("Difficulty", "Dificultad"),
            entry("Level", "Nivel"),
            entry("Algorithms", "Algoritmos"),
            entry("Techniques", "Técnicas"),
            entry("Pending", "Pendiente"),
            entry("To do", "Por hacer"),
            entry("Attempted", "Intentado"),
            entry("Solved with help", "Resuelto con ayuda"),
            entry("Solved", "Resuelto"),
            entry("Easy", "Fácil"),
            entry("Medium", "Medio"),
            entry("Hard", "Difícil"),
            entry("Very hard", "Muy difícil"),
            entry("Other", "Otro"),

            // The headings of new notes
            entry("## Key ideas", "## Ideas clave"),
            entry("## Notes", "## Notas"),
            entry("## Questions", "## Preguntas"),
            entry("## Summary", "## Resumen"),
            entry("## Instructions", "## Instrucciones"),
            entry("## Topics", "## Temas"),
            entry("## Complexity", "## Complejidad"),
            entry("## Solution", "## Solución"),
            entry("## Mistakes", "## Errores"),
            entry("## When to use", "## Cuándo usarlo"),
            entry("## Code", "## Código"),
            entry("## Examples", "## Ejemplos"),
            entry("## Statement", "## Enunciado"),
            entry("## Query", "## Consulta"),
            entry("## Result", "## Resultado"),
            entry("## Columns", "## Columnas"),
            entry("| Column | Type | Constraints |", "| Columna | Tipo | Restricciones |"),
            entry("## Create", "## Creación"),

            // The editor's page
            entry("Start writing…", "Empieza a escribir…"),
            entry("Copy", "Copiar"),
            entry("Copied", "Copiado"),
            entry("code", "código"));
}
