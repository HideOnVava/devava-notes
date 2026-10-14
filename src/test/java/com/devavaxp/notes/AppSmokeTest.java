package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Kind;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import javafx.stage.Window;
import netscape.javascript.JSObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opens the real window, CodeMirror included, and uses it as a student of the ITL would, mostly
 * without the keyboard: {@code ./mvnw verify -Dsmoke=true}. Screenshots of each step land in
 * target/smoke. The clipboard test puts back what the clipboard held (as far as JavaFX can read it).
 */
@EnabledIfSystemProperty(named = "smoke", matches = "true")
class AppSmokeTest {

    private static final String TEMPLATE = "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n}";

    private static final String IDEA = """
            Guarda el índice de cada valor en un **hash map**; para cada `x`, busca `target - x`.

            """;

    /** As Obsidian writes it: lists as "- item" lines. */
    private static final String WATERMELON = """
            ---
            judge: Codeforces
            id: 4A
            url: https://codeforces.com/problemset/problem/4/A
            difficulty: 800
            algorithms:
              - math
              - brute force
            status: Solved
            date: 2026-09-27
            ---
            ## Idea
            Dos partes pares solo si w es par y mayor que 2.
            """;

    private static final String DSU = """
            ---
            kind: snippet
            algorithms: [dsu]
            ---
            ## Cuándo usarlo
            Para unir conjuntos y saber si dos elementos están juntos.

            ## Código

            ```cpp
            int find(int x) { return p[x] == x ? x : p[x] = find(p[x]); }
            ```
            """;

    private static final String LECTURE = """
            El límite con el que empieza todo: $\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1$, y en su propia línea

            $$
            f'(x) = \\lim_{h \\to 0} \\frac{f(x + h) - f(x)}{h}
            $$

            """;

    private static final String QUERY = """
            SELECT d.department_name, COUNT(e.employee_id) AS empleados
            FROM departments d
            LEFT JOIN employees e ON e.department_id = d.department_id
            GROUP BY d.department_name
            ORDER BY empleados DESC;""";

    private static final String ANIMAL = """
            class Animal {
                protected String nombre;
                String sonido() { return "..."; }
            }

            class Perro extends Animal {
                @Override String sonido() { return "¡Guau!"; }
            }""";

    private static final String CREATE = """
            CREATE TABLE employees (
              employee_id   NUMBER(6) PRIMARY KEY,
              first_name    VARCHAR2(20),
              hire_date     DATE DEFAULT SYSDATE NOT NULL,
              department_id NUMBER(4) REFERENCES departments
            );""";

    @BeforeAll
    static void startJavaFx() {
        Platform.startup(() -> {
        });
        Platform.setImplicitExit(false);
    }

    /** The window, off screen so the test does not get in anyone's way, once the editor is ready. */
    private static NotesApp open(Path home, Stage[] stage) throws Exception {
        NotesApp app = fx(NotesApp::new);
        stage[0] = fx(() -> {
            Stage s = new Stage();
            s.setX(-4000);
            app.show(s, new Vault(home));
            return s;
        });
        waitFor("the editor to load", () -> fx(() -> app.editor.ready));
        return app;
    }

    @Test
    void usesAGeneralNotebookWithLinksTagsAndPictures(@TempDir Path home, @TempDir Path pictures) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        shot(stage, "g0-welcome");
        assertTrue(fx(() -> texts(stage)).contains("Te damos la bienvenida a Devava Notes"));
        NotebookType general = NotebookType.GENERAL;
        Path notebook = fx(() -> app.createNotebook("Ideas", general));
        fx(() -> run(() -> app.openNotebook(notebook)));
        Path readingList = fx(() -> app.createNote(general.kinds().get(0), "Lista de lectura", ""));
        Path plans = fx(() -> app.createNote(general.kinds().get(0), "Planes", ""));

        // A picture dropped on the note, as its file is: a 2 × 2 PNG.
        Path picture = pictures.resolve("foto del pizarrón.png");
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", picture.toFile());
        fx(() -> run(() -> {
            ((JSObject) engine.executeScript("window")).call("insertText", "Ver [[Lista de lectura]] y #libros #por-leer.\n\n");
            try {
                app.attachImages(List.of(picture.toFile()));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }));
        waitFor("the note to save itself", () -> Files.readString(plans).contains("![](<attachments/foto del pizarrón.png>)"));
        assertTrue(Files.exists(notebook.resolve("attachments").resolve("foto del pizarrón.png")));

        fx(() -> run(app::toggleMode));
        waitFor("the picture shown", () -> fx(() -> ((Number) engine.executeScript(
                "(document.querySelector('#reading img') || {}).naturalWidth || 0")).intValue() == 2));
        assertEquals(2, fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .tag').length")).intValue()));
        assertEquals(0, fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .wikilink.missing').length")).intValue()));
        shot(stage, "g1-plans");
        fx(() -> engine.executeScript("document.querySelector('#reading .tag').click()"));   // → Por etiqueta › libros
        shot(stage, "g2-tag");
        fx(() -> engine.executeScript("document.querySelector('#reading .wikilink').click()"));
        waitFor("the [[link]] to open its note", () -> fx(() -> readingList.equals(app.openPath())));
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /** WebKit in JavaFX empties the clipboard when a page writes to it: copy and cut go through Java. */
    @Test
    void copiesCutsAndPastesWithTheSystemClipboard(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        NotebookType general = NotebookType.GENERAL;
        Path notebook = fx(() -> app.createNotebook("Ideas", general));
        fx(() -> run(() -> app.openNotebook(notebook)));
        Path note = fx(() -> app.createNote(general.kinds().get(0), "Portapapeles", ""));
        Map<DataFormat, Object> saved = fx(AppSmokeTest::clipboard);
        try {
            fx(() -> engine.executeScript("insertText('alfa beta'); selectMatch('beta')"));
            press(app, KeyCode.C);
            assertEquals("beta", fx(() -> Clipboard.getSystemClipboard().getString()));
            fx(() -> engine.executeScript("selectMatch('alfa')"));
            press(app, KeyCode.X);
            assertEquals("alfa", fx(() -> Clipboard.getSystemClipboard().getString()));
            press(app, KeyCode.END);
            press(app, KeyCode.V);
            waitFor("the cut word pasted at the end", () -> Files.readString(note).equals(" betaalfa"));

            // A screenshot: no text on the clipboard, a picture saved in attachments/.
            fx(() -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.IMAGE, new WritableImage(3, 2))));
            press(app, KeyCode.V);
            waitFor("the picture pasted", () -> Files.readString(note).contains("![](<attachments/Imagen pegada "));
        } finally {
            fx(() -> Clipboard.getSystemClipboard().setContent(saved));
        }
        fx(() -> run(window[0]::close));
    }

    @Test
    void usesAClassNotesNotebook(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        NotebookType course = NotebookType.CLASS_NOTES;
        LocalDate today = LocalDate.now();
        Path notebook = fx(() -> app.createNotebook("Cálculo Vectorial", course));
        fx(() -> run(() -> app.openNotebook(notebook)));

        Path limits = fx(() -> app.createNote(course.kinds().get(0), "Límites", ""));
        assertTrue(Files.readString(limits).contains("## Ideas clave"));
        fx(() -> run(() -> {
            type(stage, "unit", "1. Límites");
            ((JSObject) engine.executeScript("window")).call("insertText", LECTURE);
        }));
        waitFor("the lecture to save itself", () -> Files.readString(limits).contains("\\frac{\\sin x}{x}"));
        Path homework = fx(() -> app.createNote(course.kinds().get(1), "Tarea 3", ""));
        fx(() -> run(() -> ((DatePicker) stage.getScene().lookup("#property-due")).setValue(today.plusDays(1))));
        waitFor("the due date to be saved", () -> Files.readString(homework).contains("due: " + today.plusDays(1)));

        // Written elsewhere: a late assignment, one already done, and an exam.
        Files.writeString(notebook.resolve("Tarea 2.md"), "---\nkind: assignment\ndue: " + today.minusDays(3) + "\nstatus: Pending\n---\n");
        Files.writeString(notebook.resolve("Tarea 1.md"), "---\nkind: assignment\ndue: " + today.minusDays(9) + "\nstatus: Done\n---\n");
        Files.writeString(notebook.resolve("Segundo parcial.md"), "---\nkind: exam\ndate: " + today.plusDays(5)
                + "\ntopics: [límites, derivadas]\n---\n");
        fx(() -> run(app::refreshFromDisk));
        fx(() -> run(() -> app.browser.choose("Lo que se acerca", null)));
        shot(stage, "c1-upcoming");
        assertTrue(fx(() -> texts(stage)).containsAll(List.of("3 días de retraso", "Vence mañana", "En 5 días")));

        fx(() -> run(() -> app.setProperty(notebook.resolve("Tarea 2.md"), "status", "Done")));   // "Marcar como hecha"
        assertTrue(Files.readString(notebook.resolve("Tarea 2.md")).contains("status: Done"));   // kept as earlier versions wrote it

        fx(() -> run(() -> app.openNote(limits)));
        fx(() -> run(app::toggleMode));
        waitFor("the formulas drawn by KaTeX", () -> fx(() ->
                ((Number) engine.executeScript("document.querySelectorAll('#reading .katex').length")).intValue() == 2));
        waitFor("KaTeX's fonts", () -> fx(() -> Boolean.TRUE.equals(engine.executeScript(
                "[...document.fonts].some(f => f.family.replace(/\"/g, '') === 'KaTeX_Main' && f.status === 'loaded')"))));
        shot(stage, "c2-lecture");
        fx(() -> run(app::toggleMode));

        fx(() -> run(app::showHome));
        shot(stage, "c3-home");
        assertEquals(2, fx(() -> stage.getScene().getRoot().lookupAll(".upcoming-row").size()));   // Tarea 3 and the exam
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    @Test
    void usesACompetitiveProgrammingNotebook(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());

        NotebookType cp = NotebookType.COMPETITIVE_PROGRAMMING;
        Kind problem = cp.kinds().get(0);
        Path notebook = fx(() -> app.createNotebook("Algoritmos", cp));
        Vault.setMeta(notebook, "cpp", TEMPLATE + "\n");
        fx(() -> run(() -> app.openNotebook(notebook)));

        // A LeetCode link: judge and id filled in, and no C++ main() in its solution.
        Path twoSum = fx(() -> app.createNote(problem, "Two Sum", "https://leetcode.com/problems/two-sum/"));
        String created = Files.readString(twoSum);
        assertTrue(created.startsWith("---\nstatus: To do\ndate: " + LocalDate.now()
                + "\nurl: https://leetcode.com/problems/two-sum/\njudge: LeetCode\nid: two-sum\n---\n"), created);
        assertTrue(created.contains("## Solución\n\n```cpp\n\n```"), created);
        fx(() -> run(() -> {
            type(stage, "difficulty", "Easy");
            type(stage, "algorithms", "hash map, arrays");
            choose(stage, "status", "Solved");
            ((JSObject) engine.executeScript("window")).call("insertText", IDEA);
        }));
        waitFor("the note to save itself", () -> Files.readString(twoSum).contains("busca `target - x`"));
        assertTrue(Files.readString(twoSum).contains("algorithms: [hash map, arrays]"));

        // A Codeforces link: its solution starts with the notebook's C++ template.
        Path cf = fx(() -> app.createNote(problem, "CF 1850G", "https://codeforces.com/contest/1850/problem/G"));
        String cfText = Files.readString(cf);
        assertTrue(cfText.contains("judge: Codeforces\nid: 1850G\n") && cfText.contains("```cpp\n" + TEMPLATE + "\n```"), cfText);
        fx(() -> run(() -> {
            type(stage, "difficulty", "1700");
            type(stage, "algorithms", "graphs/dijkstra, math");
            choose(stage, "status", "Solved with help");
        }));

        // Written elsewhere (Obsidian): a problem and a snippet of the code library.
        Files.writeString(notebook.resolve("CF 4A - Watermelon.md"), WATERMELON);
        Files.writeString(notebook.resolve("DSU.md"), DSU);
        fx(() -> run(app::refreshFromDisk));
        fx(() -> run(() -> app.insertSnippet(notebook.resolve("DSU.md"))));
        waitFor("the snippet to be saved in the problem", () -> Files.readString(cf).contains("int find(int x)"));
        fx(() -> run(() -> app.browser.choose("Todos los problemas", null)));
        shot(stage, "1-problem");

        fx(() -> run(() -> app.browser.choose("Por algoritmo", "graphs")));
        shot(stage, "2-graphs");
        fx(() -> run(() -> app.browser.choose("Por dificultad", null)));
        shot(stage, "3-by-difficulty");
        assertTrue(fx(() -> texts(stage)).containsAll(List.of("Fácil  2", "Difícil  1")));   // the groups, with their counts
        fx(() -> run(() -> {
            app.browser.choose("Todos los problemas", null);
            app.browser.showTable(true);
        }));
        shot(stage, "4-table");
        fx(() -> run(() -> {
            app.browser.showTable(false);
            app.toggleMode();
        }));
        waitFor("C++ colored in the reading view", () -> fx(() ->
                ((Number) engine.executeScript("document.querySelectorAll('#reading .tok-keyword').length")).intValue() > 0));
        assertEquals(2, fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .code-card .copy').length")).intValue()));
        shot(stage, "5-read");
        fx(() -> run(app::showHome));
        shot(stage, "6-home");
        dialogShot(stage, "d1-new-notebook", app::newNotebook, "");
        dialogShot(stage, "d4-help", app::showHelp, "");

        // The dark theme, remembered for the next start, and the editor's page follows it.
        fx(() -> run(() -> app.setDark(true)));
        assertTrue(Files.readString(home.resolve("settings.json")).contains("\"dark\""));
        shot(stage, "7-dark-home");
        dialogShot(stage, "d2-dark-quick-open", app::quickOpen, "");
        dialogShot(stage, "d3-dark-search", app::search, "find");
        fx(() -> run(() -> {
            app.openNotebook(notebook);
            app.openNote(cf);
        }));
        waitFor("the editor's page in dark", () -> fx(() -> "dark".equals(engine.executeScript("document.documentElement.dataset.theme"))));
        waitFor("C++ colored in the reading view", () -> fx(() ->
                ((Number) engine.executeScript("document.querySelectorAll('#reading .tok-keyword').length")).intValue() > 0));
        shot(stage, "8-dark-read");
        fx(() -> run(app::toggleMode));
        shot(stage, "9-dark-edit");
        fx(() -> run(() -> app.browser.showTable(true)));
        shot(stage, "10-dark-table");

        assertEquals(WATERMELON, Files.readString(notebook.resolve("CF 4A - Watermelon.md")));   // only read, never rewritten
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    @Test
    void usesADatabasesNotebook(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        JSObject page = fx(() -> (JSObject) engine.executeScript("window"));
        NotebookType db = NotebookType.DATABASES;
        Path notebook = fx(() -> app.createNotebook("Taller de Base de Datos", db));
        fx(() -> run(() -> app.openNotebook(notebook)));

        // An exercise: its statement, its topics and level, and the query in the PL/SQL block of its template.
        Path exercise = fx(() -> app.createNote(kind(db, "exercise"), "Empleados por departamento", ""));
        fx(() -> run(() -> {
            type(stage, "topics", "consultas/joins, consultas/group by");
            choose(stage, "level", "Medium");
            choose(stage, "status", "Solved");
            page.call("selectMatch", "## Enunciado");
            page.call("insertText", "## Enunciado\n\n¿Cuántos empleados trabajan en cada departamento, incluidos los que no tienen ninguno?");
            page.call("selectMatch", "```plsql");
            page.call("insertText", "```plsql\n" + QUERY);
        }));
        waitFor("the exercise to save itself", () -> Files.readString(exercise).contains("LEFT JOIN employees e"));
        assertTrue(Files.readString(exercise).contains("topics: [consultas/joins, consultas/group by]"));
        shot(stage, "db1-exercise");
        fx(() -> run(app::toggleMode));
        waitFor("PL/SQL colored in the reading view", () -> fx(() ->
                ((Number) engine.executeScript("document.querySelectorAll('#reading .tok-keyword').length")).intValue() > 5));
        assertEquals("PL/SQL", fx(() -> engine.executeScript("document.querySelector('#reading .code-head .lang').textContent")));
        shot(stage, "db2-reading");
        fx(() -> run(app::toggleMode));

        // A table, its CREATE in a block of the bar's button, in the notebook's PL/SQL: Oracle's types
        // (VARCHAR2, NUMBER) are colored as types.
        Path table = fx(() -> app.createNote(kind(db, "table"), "EMPLOYEES", ""));
        fx(() -> run(() -> {
            button(stage, "Bloque de código").fire();
            page.call("insertText", CREATE);
        }));
        waitFor("the table to save itself", () -> Files.readString(table).contains("```plsql\n" + CREATE + "\n```"));
        fx(() -> run(app::toggleMode));
        waitFor("VARCHAR2 colored as a type", () -> fx(() -> Boolean.TRUE.equals(engine.executeScript(
                "[...document.querySelectorAll('#reading .tok-typeName')].some(t => t.textContent === 'VARCHAR2')"))));
        fx(() -> run(app::toggleMode));

        // An exam written elsewhere: in "Lo que se acerca" here and on Home, as in Class Notes.
        LocalDate today = LocalDate.now();
        Files.writeString(notebook.resolve("Segundo examen.md"), "---\nkind: exam\ndate: " + today.plusDays(2) + "\ntopics: [plsql/cursores]\n---\n");
        fx(() -> run(app::refreshFromDisk));
        fx(() -> run(() -> app.browser.choose("Por tema", "consultas")));
        shot(stage, "db3-by-topic");
        fx(() -> run(() -> app.browser.choose("Lo que se acerca", null)));
        shot(stage, "db4-upcoming");
        fx(() -> run(app::showHome));
        shot(stage, "db5-home");
        assertEquals(1, fx(() -> stage.getScene().getRoot().lookupAll(".upcoming-row").size()));

        fx(() -> run(() -> {
            app.setDark(true);
            app.openNotebook(notebook);
            app.browser.choose("Ejercicios", null);
            app.openNote(exercise);
            app.toggleMode();
        }));
        waitFor("the reading view again", () -> fx(() ->
                ((Number) engine.executeScript("document.querySelectorAll('#reading .tok-keyword').length")).intValue() > 5));
        shot(stage, "db6-dark");
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /** A class of POO with its code: blocks added, given a language, written, copied, guarded and removed. */
    @Test
    void writesACodeSession(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        JSObject page = fx(() -> (JSObject) engine.executeScript("window"));
        NotebookType course = NotebookType.CLASS_NOTES;
        Path notebook = fx(() -> app.createNotebook("Programación Orientada a Objetos", course));
        fx(() -> run(() -> app.openNotebook(notebook)));
        Path session = fx(() -> app.createNote(course.kinds().get(0), "Sesión 7 · Herencia", ""));

        // The notebook has no language yet: its first block asks for one, which the notebook keeps.
        fx(() -> run(() -> {
            page.call("selectMatch", "## Notas");
            page.call("insertText", "## Notas\n\nUna clase hija hereda los atributos y métodos de su clase padre con **extends**.");
            button(stage, "Lista").fire();
            button(stage, "Bloque de código").fire();
        }));
        waitFor("the menu of languages", () -> fx(() -> Boolean.TRUE.equals(engine.executeScript("!!document.querySelector('.lang-menu')"))));
        shot(stage, "s1-language-menu");
        fx(() -> engine.executeScript("[...document.querySelectorAll('.lang-menu .option')].find(o => o.textContent.startsWith('Java')).click()"));
        waitFor("a Java block", () -> Files.readString(session).contains("- Una clase hija") && Files.readString(session).contains("```java\n\n```"));
        waitFor("the notebook's language", () -> Vault.meta(notebook, "language").equals("java"));
        assertTrue(fx(() -> texts(stage)).contains("Java"));   // its chip, in the bar
        fx(() -> run(() -> page.call("insertText", ANIMAL)));
        waitFor("the code saved", () -> Files.readString(session).contains("```java\n" + ANIMAL + "\n```"));

        // Another block at the end, in Java now, turned into the output of the program.
        fx(() -> engine.executeScript("document.querySelector('.cm-add-block').click()"));
        waitFor("a second block", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-card-head').length")).intValue() == 2));
        fx(() -> engine.executeScript("[...document.querySelectorAll('.cm-card-head .chip')].at(-1).click()"));
        fx(() -> engine.executeScript("[...document.querySelectorAll('.lang-menu .option')].find(o => o.textContent.startsWith('Salida')).click()"));
        fx(() -> run(() -> page.call("insertText", "¡Guau!")));
        waitFor("the output saved", () -> Files.readString(session).contains("```output\n¡Guau!\n```"));
        assertEquals("Java,Salida", fx(() -> engine.executeScript(
                "[...document.querySelectorAll('.cm-card-head .chip')].map(c => c.textContent).join()")));
        shot(stage, "s2-session-edit");

        // Copiar puts the code on the clipboard, as it is.
        Map<DataFormat, Object> saved = fx(AppSmokeTest::clipboard);
        try {
            fx(() -> engine.executeScript("document.querySelector('.cm-card-head .act').click()"));
            waitFor("the code on the clipboard", () -> fx(() -> ANIMAL.equals(Clipboard.getSystemClipboard().getString())));
        } finally {
            fx(() -> Clipboard.getSystemClipboard().setContent(saved));
        }

        // A Backspace at the start of the code leaves the block whole.
        fx(() -> run(() -> page.call("selectMatch", "class Animal")));
        key(app, KeyCode.LEFT);
        key(app, KeyCode.BACK_SPACE);
        Thread.sleep(900);   // past the autosave
        assertTrue(Files.readString(session).contains("```java\nclass Animal {"), Files.readString(session));

        // Quitar asks once more for a block with code in it.
        fx(() -> engine.executeScript("[...document.querySelectorAll('.cm-card-head')].at(-1).querySelectorAll('.act')[1].click()"));
        assertTrue(Files.readString(session).contains("```output"));
        fx(() -> engine.executeScript("[...document.querySelectorAll('.cm-card-head')].at(-1).querySelectorAll('.act')[1].click()"));
        waitFor("the output removed", () -> !Files.readString(session).contains("```output"));

        fx(() -> run(app::toggleMode));
        waitFor("Java colored in the reading view", () -> fx(() ->
                ((Number) engine.executeScript("document.querySelectorAll('#reading .tok-keyword').length")).intValue() > 3));
        assertEquals("Java", fx(() -> engine.executeScript("document.querySelector('#reading .code-head .lang').textContent")));
        shot(stage, "s3-session-read");
        fx(() -> run(() -> {
            app.setDark(true);
            app.toggleMode();
        }));
        waitFor("the editor's page in dark", () -> fx(() -> "dark".equals(engine.executeScript("document.documentElement.dataset.theme"))));
        shot(stage, "s4-session-dark");
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /** The ``` lines of a card are out of sight, and typing next to a card never breaks them. */
    @Test
    void keepsCodeBlocksWhole(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        JSObject page = fx(() -> (JSObject) engine.executeScript("window"));
        Path notebook = fx(() -> app.createNotebook("Estructura de Datos", NotebookType.CLASS_NOTES));
        Path note = notebook.resolve("Pilas.md");
        Files.writeString(note, "Una pila:\n\n```java\nStack<Integer> pila = new Stack<>();\n```");
        fx(() -> run(() -> {
            app.openNotebook(notebook);
            app.openNote(note);
        }));
        waitFor("the card", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-card-head').length")).intValue() == 1));

        // Typed right after the card (at the end of the note), the text goes on a line of its own.
        fx(() -> run(app.editor::focus));
        press(app, KeyCode.END);
        fx(() -> run(() -> page.call("insertText", "Y una cola.")));
        waitFor("the text below the card", () -> Files.readString(note).endsWith("pila = new Stack<>();\n```\nY una cola."));

        // Enter after ```python closes the block; Backspace in it, still empty, removes it.
        fx(() -> run(() -> page.call("insertText", "\n```python")));
        key(app, KeyCode.ENTER);
        waitFor("a closed block", () -> Files.readString(note).endsWith("Y una cola.\n```python\n\n```"));
        key(app, KeyCode.BACK_SPACE);
        waitFor("the empty block removed", () -> Files.readString(note).endsWith("Y una cola."));

        // Ctrl+B: bold.
        fx(() -> run(() -> page.call("selectMatch", "una cola")));
        press(app, KeyCode.B);
        waitFor("bold", () -> Files.readString(note).endsWith("Y **una cola**."));

        // Más lenguajes…: any language CodeMirror knows, found by typing part of its name.
        fx(() -> engine.executeScript("document.querySelector('.cm-card-head .chip').click()"));
        fx(() -> engine.executeScript("[...document.querySelectorAll('.lang-menu .option')].at(-1).click()"));
        fx(() -> engine.executeScript("const f = document.querySelector('.lang-menu .find'); f.value = 'kot'; f.dispatchEvent(new Event('input'))"));
        shot(window[0], "s5-more-languages");
        fx(() -> engine.executeScript("document.querySelector('.lang-menu .list .option').click()"));
        waitFor("a Kotlin block", () -> Files.readString(note).contains("```kotlin\nStack<Integer> pila = new Stack<>();\n```\n"));
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(window[0]::close));
    }

    /** Nueva materia: a subject found in the curriculum, with its type, language and topics; its empty views say how to start. */
    @Test
    void createsASubjectFromTheCurriculum(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        fx(() -> app.createNotebook("Ideas", NotebookType.GENERAL));   // Home, not the first use
        fx(() -> run(app::showHome));
        Platform.runLater(app::newNotebook);   // it stays in showAndWait() until the dialog closes
        waitFor("the dialog of Nueva materia", () -> fx(() -> dialog(stage) != null));
        Stage dialog = fx(() -> dialog(stage));
        fx(() -> run(() -> ((TextField) dialog.getScene().lookup(".text-field")).setText("estruc")));
        shot(dialog, "d5-new-subject");
        List<String> shown = fx(() -> texts(dialog));
        assertTrue(shown.containsAll(List.of("3ER SEMESTRE", "Estructura de Datos", "Programación · Java", "Crear “estruc” con otro nombre")),
                shown.toString());
        fx(() -> run(() -> button(dialog, "Crear materia").fire()));
        Path ed = home.resolve("Estructura de Datos");
        waitFor("the notebook of Estructura de Datos", () -> Files.isDirectory(ed) && Vault.type(ed) == NotebookType.PROGRAMMING);
        assertEquals("java", Vault.meta(ed, "language"));
        assertEquals("B13", Vault.meta(ed, "subject"));
        assertEquals(Period.of(LocalDate.now()).toString(), Vault.meta(ed, "period"));

        // Its views, empty, say what goes in them, with the button for the first one.
        waitFor("its first view, empty", () -> fx(() -> texts(stage)).contains("Aún no tienes apuntes en Estructura de Datos"));
        fx(() -> run(() -> app.browser.choose("Sesiones de código", null)));
        shot(stage, "e1-empty-sessions");
        assertTrue(fx(() -> texts(stage)).containsAll(List.of("Aún no tienes sesiones de código en Estructura de Datos", "Nueva sesión de código")));

        // A session starts with a block in Java and one for its output; its topics suggest the subject's.
        Path session = fx(() -> app.createNote(kind(NotebookType.PROGRAMMING, "session"), "Sesión 1 · Pilas", ""));
        assertTrue(Files.readString(session).contains("## Código\n\n```java\n\n```\n\n## Salida\n\n```output\n\n```"), Files.readString(session));
        MenuButton topics = fx(() -> (MenuButton) stage.getScene().lookup(".picker"));
        fx(() -> run(topics::show));
        List<String> offered = fx(() -> topics.getItems().stream().map(MenuItem::getText).toList());
        fx(() -> run(topics::hide));
        assertTrue(offered.containsAll(List.of("pilas", "colas", "listas enlazadas")), offered.toString());
        shot(stage, "e2-session");
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /** The first time: the subjects of the semester, ticked in the curriculum, and a note on how the app works. */
    @Test
    void startsWithTheSubjectsOfTheSemester(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        assertTrue(fx(() -> texts(stage)).contains("Te damos la bienvenida a Devava Notes"));
        TextField find = fx(() -> (TextField) stage.getScene().lookup(".first-use .text-field"));
        fx(() -> run(() -> {
            find.setText("datos");
            checkBox(stage, "Estructura de Datos").setSelected(true);
            checkBox(stage, "Fundamentos de Bases de Datos").setSelected(true);
        }));
        shot(stage, "readme-primer-uso");
        fx(() -> run(() -> {
            checkBox(stage, "Estructura de Datos").setSelected(false);
            checkBox(stage, "Fundamentos de Bases de Datos").setSelected(false);
            find.setText("programación");
        }));
        fx(() -> run(() -> {
            checkBox(stage, "Fundamentos de Programación").setSelected(true);
            checkBox(stage, "Programación Orientada a Objetos").setSelected(true);
        }));
        shot(stage, "f1-first-use");
        fx(() -> run(() -> button(stage, "Crear 2 materias").fire()));
        Path poo = home.resolve("Programación Orientada a Objetos");
        assertEquals(NotebookType.PROGRAMMING, Vault.type(poo));
        assertEquals("java", Vault.meta(poo, "language"));
        assertEquals("cpp", Vault.meta(home.resolve("Fundamentos de Programación"), "language"));
        Path guide = home.resolve("Primeros pasos").resolve("Cómo usar Devava Notes.md");
        assertTrue(Files.readString(guide).contains("```java"));
        shot(stage, "f2-home");
        assertTrue(fx(() -> texts(stage)).containsAll(List.of("Programación Orientada a Objetos", "Programación · Java", "Primeros pasos")));

        // The guide, as it reads: its formula, its code and its output.
        fx(() -> run(() -> {
            app.openNotebook(home.resolve("Primeros pasos"));
            app.openNote(guide);
            app.toggleMode();
        }));
        waitFor("the guide's two blocks", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .code-card').length")).intValue() == 2));
        waitFor("its formula", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .katex').length")).intValue() == 1));
        shot(stage, "f3-guide");
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /** A session of Estructura de Datos, as a student writes it. */
    private static final String STACK = """
            ---
            kind: session
            date: %s
            unit: 3. Estructuras lineales
            topics: [pilas]
            ---
            ## Objetivo

            Usar una pila para invertir una palabra: el último en entrar es el primero en salir.

            ## Código

            ```java
            import java.util.ArrayDeque;
            import java.util.Deque;

            public class Pila {
                public static void main(String[] args) {
                    Deque<Character> pila = new ArrayDeque<>();
                    for (char c : "Tec".toCharArray()) pila.push(c);
                    StringBuilder invertida = new StringBuilder();
                    while (!pila.isEmpty()) invertida.append(pila.pop());
                    System.out.println(invertida);
                }
            }
            ```

            ## Salida

            ```output
            ceT
            ```

            ## Notas

            `push` mete arriba y `pop` saca de arriba, los dos en O(1).
            """;

    private static final String PARTIAL = """
            ---
            date: %s
            unit: 2. Funciones de varias variables
            topic: derivadas parciales
            ---
            ## Ideas clave

            Para derivar respecto a $x$, las demás variables se quedan fijas:

            $$
            \\frac{\\partial f}{\\partial x} = \\lim_{h \\to 0} \\frac{f(x + h, y) - f(x, y)}{h}
            $$

            Con $f(x, y) = x^2 y + \\sin y$ queda $f_x = 2xy$ y $f_y = x^2 + \\cos y$.

            ## El gradiente

            $$
            \\nabla f = \\left( \\frac{\\partial f}{\\partial x}, \\frac{\\partial f}{\\partial y} \\right)
            $$

            Apunta hacia donde $f$ crece más rápido.
            """;

    /**
     * The screenshots of the README, a semester of ISC in the app (target/smoke/readme-*.png), and
     * the dialogs that go with them, light and dark, to look over.
     */
    @Test
    void takesTheReadmeScreenshots(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        LocalDate today = LocalDate.now();
        String now = Period.of(today).toString(), before = Period.of(today).previous().previous().toString();
        Path ed = subject(app, "Estructura de Datos", NotebookType.PROGRAMMING, "java", now);
        Path db = subject(app, "Taller de Bases de Datos", NotebookType.DATABASES, "plsql", now);
        Path calculus = subject(app, "Cálculo Vectorial", NotebookType.CLASS_NOTES, "", now);
        Path physics = subject(app, "Física General", NotebookType.CLASS_NOTES, "", now);
        Path cp = subject(app, "Algoritmos", NotebookType.COMPETITIVE_PROGRAMMING, "", null);
        subject(app, "Programación Orientada a Objetos", NotebookType.PROGRAMMING, "java", before);
        subject(app, "Cálculo Integral", NotebookType.CLASS_NOTES, "", before);
        Path stack = ed.resolve("Sesión 5 · Pilas.md");
        Files.writeString(stack, STACK.formatted(today.minusDays(2)));
        Files.writeString(ed.resolve("Sesión 6 · Colas.md"), "---\nkind: session\ndate: " + today + "\nunit: 3. Estructuras lineales\ntopics: [colas]\n---\n");
        Files.writeString(ed.resolve("Recursividad.md"), "---\ndate: " + today.minusDays(9) + "\nunit: 2. Recursividad\n---\n");
        Files.writeString(ed.resolve("Práctica 3 · Listas enlazadas.md"), "---\nkind: assignment\ndue: " + today.plusDays(2) + "\nstatus: Pending\n---\n");
        Files.writeString(ed.resolve("Primer parcial.md"), "---\nkind: exam\ndate: " + today.plusDays(6) + "\ntopics: [pilas, colas]\n---\n");
        Path exercise = db.resolve("Empleados por departamento.md");
        Files.writeString(exercise, "---\nkind: exercise\ntopics: [consultas/joins, consultas/group by]\nlevel: Medium\nstatus: Solved\ndate: "
                + today + "\n---\n## Enunciado\n\n¿Cuántos empleados trabajan en cada departamento, incluidos los que no tienen ninguno?\n\n"
                + "## Consulta\n\n```plsql\n" + QUERY + "\n```\n");
        Files.writeString(db.resolve("EMPLOYEES.md"), "---\nkind: table\n---\n## Creación\n\n```plsql\n" + CREATE + "\n```\n");
        Files.writeString(db.resolve("Segundo examen.md"), "---\nkind: exam\ndate: " + today.plusDays(9) + "\ntopics: [plsql/cursores]\n---\n");
        Path partial = calculus.resolve("Derivadas parciales.md");
        Files.writeString(partial, PARTIAL.formatted(today.minusDays(1)));
        Files.writeString(calculus.resolve("Tarea 3.md"), "---\nkind: assignment\ndue: " + today.plusDays(1) + "\nstatus: Pending\n---\n");
        Files.writeString(physics.resolve("Leyes de Newton.md"), "---\ndate: " + today.minusDays(3) + "\nunit: 2. Dinámica\n---\n");
        Files.writeString(cp.resolve("CF 4A - Watermelon.md"), WATERMELON);
        Files.writeString(cp.resolve("DSU.md"), DSU);
        age(home, Map.of(ed.resolve("Sesión 6 · Colas.md"), 25L, partial, 190L, stack, 1560L, exercise, 2900L,
                physics.resolve("Leyes de Newton.md"), 4400L, calculus.resolve("Tarea 3.md"), 5800L));

        fx(() -> run(app::showHome));
        shot(stage, "readme-inicio");
        dialogShot(stage, "readme-nueva-materia", app::newNotebook, "bases de datos");
        fx(() -> run(() -> {
            app.openNotebook(ed);
            app.browser.choose("Sesiones de código", null);
            app.openNote(stack);
        }));
        waitFor("the session's cards", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-card-head').length")).intValue() == 2));
        shot(stage, "readme-sesion-de-codigo");
        fx(() -> run(() -> {
            app.openNotebook(calculus);
            app.openNote(partial);
            app.toggleMode();
        }));
        waitFor("the formulas", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .katex').length")).intValue() >= 4));
        waitFor("KaTeX's fonts", () -> fx(() -> Boolean.TRUE.equals(engine.executeScript(
                "[...document.fonts].some(f => f.family.replace(/\"/g, '') === 'KaTeX_Main' && f.status === 'loaded')"))));
        shot(stage, "readme-materia");
        fx(() -> run(app::toggleMode));

        // The dialogs of a notebook, in light, then everything new in dark.
        dialogShot(stage, "r1-change-type", () -> menuItem(stage, "Cambiar tipo…").fire(), "");
        dialogShot(stage, "r2-language", () -> menuItem(stage, "Lenguaje del código…").fire(), "");
        dialogShot(stage, "r3-period", () -> menuItem(stage, "Cambiar periodo…").fire(), "");
        fx(() -> run(() -> {
            app.setDark(true);
            app.openNotebook(db);
            app.browser.choose("Ejercicios", null);
            app.openNote(exercise);
        }));
        waitFor("the editor's page in dark", () -> fx(() -> "dark".equals(engine.executeScript("document.documentElement.dataset.theme"))));
        waitFor("the exercise's card", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-card-head').length")).intValue() == 1));
        shot(stage, "readme-bases-de-datos-oscuro");
        fx(() -> run(() -> app.browser.choose("Sesiones de código", null)));
        shot(stage, "r4-dark-empty");
        fx(() -> run(() -> {
            app.openNotebook(ed);
            app.openNote(stack);
            app.toggleMode();
        }));
        waitFor("the session read in dark", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .code-card').length")).intValue() == 2));
        shot(stage, "r5-dark-session");
        fx(() -> run(app::toggleMode));
        dialogShot(stage, "r6-dark-change-type", () -> menuItem(stage, "Cambiar tipo…").fire(), "");
        fx(() -> run(app::showHome));
        shot(stage, "r7-dark-home");
        dialogShot(stage, "r8-dark-new-subject", app::newNotebook, "estruc");
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /** Home shows this school period's notebooks; the earlier ones fold away until asked for. */
    @Test
    void homeGroupsNotebooksBySchoolPeriod(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        Period now = Period.of(LocalDate.now()), before = now.previous().previous();
        fx(() -> app.createNotebook("Programación Orientada a Objetos", NotebookType.CLASS_NOTES));
        Path fp = fx(() -> app.createNotebook("Fundamentos de Programación", NotebookType.CLASS_NOTES));
        Vault.setMeta(fp, "period", before.toString());
        fx(() -> run(app::showHome));
        shot(stage, "p1-home-periods");
        List<String> shown = fx(() -> texts(stage));
        assertTrue(shown.containsAll(List.of(now.toString(), "Programación Orientada a Objetos", "Semestres anteriores · 1 materia")), shown.toString());
        assertFalse(shown.contains("Fundamentos de Programación"));

        fx(() -> run(() -> stage.getScene().getRoot().lookupAll(".button").stream().map(Button.class::cast)
                .filter(b -> b.getText() != null && b.getText().startsWith("Semestres anteriores")).findFirst().orElseThrow().fire()));
        shot(stage, "p2-home-older");
        assertTrue(fx(() -> texts(stage)).containsAll(List.of(before.toString(), "Fundamentos de Programación")));
        fx(() -> run(stage::close));
    }

    /** Home with a notebook of each type, light and dark: the screenshots of the README. */
    @Test
    void showsANotebookOfEachTypeOnHome(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        LocalDate today = LocalDate.now();
        Path ideas = fx(() -> app.createNotebook("Ideas", NotebookType.GENERAL));
        Path calculus = fx(() -> app.createNotebook("Cálculo Vectorial", NotebookType.CLASS_NOTES));
        Path algorithms = fx(() -> app.createNotebook("Algoritmos", NotebookType.COMPETITIVE_PROGRAMMING));
        Path databases = fx(() -> app.createNotebook("Taller de Base de Datos", NotebookType.DATABASES));
        Path poo = fx(() -> app.createNotebook("Programación Orientada a Objetos", NotebookType.PROGRAMMING));
        Vault.setMeta(poo, "language", "java");
        Files.writeString(poo.resolve("Sesión 7 · Herencia.md"), "---\nkind: session\ndate: " + today + "\nunit: 3. Herencia\n---\n## Código\n\n```java\n"
                + ANIMAL + "\n```\n");
        Files.writeString(poo.resolve("Práctica 4 · Herencia.md"), "---\nkind: assignment\ndue: " + today.plusDays(2) + "\nstatus: Pending\n---\n");
        Files.writeString(databases.resolve("Empleados por departamento.md"), "---\nkind: exercise\ntopics: [consultas/joins]\n---\n");
        Files.writeString(databases.resolve("Segundo examen.md"), "---\nkind: exam\ndate: " + today.plusDays(9) + "\n---\n");
        Files.writeString(ideas.resolve("Lista de lectura.md"), "---\ntags: [libros]\n---\n");
        Files.writeString(ideas.resolve("Proyectos.md"), "");
        Files.writeString(calculus.resolve("Límites.md"), "---\nkind: lecture\ndate: " + today + "\nunit: 1. Límites\n---\n" + LECTURE);
        Files.writeString(calculus.resolve("Tarea 3.md"), "---\nkind: assignment\ndue: " + today.plusDays(1) + "\nstatus: Pending\n---\n");
        Files.writeString(calculus.resolve("Segundo parcial.md"), "---\nkind: exam\ndate: " + today.plusDays(5) + "\n---\n");
        Files.writeString(algorithms.resolve("CF 4A - Watermelon.md"), WATERMELON);
        Files.writeString(algorithms.resolve("DSU.md"), DSU);
        fx(() -> run(app::showHome));
        shot(stage, "h1-home");
        fx(() -> run(() -> app.setDark(true)));
        shot(stage, "h2-home-dark");
        fx(() -> run(stage::close));
    }

    /** The texts a window shows: its labels, buttons and cells. */
    private static List<String> texts(Window window) {
        List<String> texts = new ArrayList<>();
        Deque<Node> nodes = new ArrayDeque<>(List.of(window.getScene().getRoot()));
        while (!nodes.isEmpty()) {
            Node node = nodes.pop();
            if (node instanceof Labeled l && l.getText() != null) texts.add(l.getText());
            if (node instanceof Parent p) nodes.addAll(p.getChildrenUnmodifiable());
        }
        return texts;
    }

    @SuppressWarnings("unchecked")
    private static void choose(Stage stage, String key, String value) {
        ((ComboBox<String>) stage.getScene().lookup("#property-" + key)).getSelectionModel().select(value);
    }

    private static void type(Stage stage, String key, String text) {
        TextField field = (TextField) stage.getScene().lookup("#property-" + key);
        field.setText(text);
        field.fireEvent(new ActionEvent());
    }

    /** Every file written days ago, but for these notes, edited so many minutes ago: what Home lists as recent. */
    private static void age(Path home, Map<Path, Long> minutesAgo) throws Exception {
        FileTime old = FileTime.from(Instant.now().minus(Duration.ofDays(12)));
        try (Stream<Path> all = Files.walk(home)) {
            for (Path p : all.toList()) if (!p.equals(home)) Files.setLastModifiedTime(p, old);
        }
        for (Map.Entry<Path, Long> e : minutesAgo.entrySet()) {
            Files.setLastModifiedTime(e.getKey(), FileTime.from(Instant.now().minus(Duration.ofMinutes(e.getValue()))));
        }
    }

    /** A notebook of a subject, with its language and period. */
    private static Path subject(NotesApp app, String name, NotebookType type, String language, String period) throws Exception {
        Path dir = fx(() -> app.createNotebook(name, type));
        if (!language.isEmpty()) Vault.setMeta(dir, "language", language);
        if (period != null) Vault.setMeta(dir, "period", period);
        return dir;
    }

    /** An item of one of the window's menus (Opciones). */
    private static MenuItem menuItem(Stage stage, String text) {
        return stage.getScene().getRoot().lookupAll(".menu-button").stream().map(MenuButton.class::cast)
                .flatMap(m -> m.getItems().stream()).filter(i -> text.equals(i.getText())).findFirst().orElseThrow();
    }

    private static Kind kind(NotebookType type, String id) {
        return type.kinds().stream().filter(k -> k.id().equals(id)).findFirst().orElseThrow();
    }

    private static CheckBox checkBox(Stage stage, String text) {
        return stage.getScene().getRoot().lookupAll(".check-box").stream().map(CheckBox.class::cast)
                .filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    /** The button of the window that says this. */
    private static Button button(Stage stage, String text) {
        return stage.getScene().getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    /** A key, alone, as the keyboard sends it to the editor. */
    private static void key(NotesApp app, KeyCode key) throws Exception {
        fx(() -> run(() -> {
            app.editor.view.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
            app.editor.view.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, false, false, false, false));
        }));
        Thread.sleep(150);
    }

    /** Ctrl + the key, as the keyboard sends it to the editor. */
    private static void press(NotesApp app, KeyCode key) throws Exception {
        fx(() -> run(() -> {
            app.editor.view.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, true, false, false));
            app.editor.view.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, false, true, false, false));
        }));
        Thread.sleep(300);
    }

    /** What the clipboard holds now, to put it back after the test. */
    private static Map<DataFormat, Object> clipboard() {
        Clipboard clip = Clipboard.getSystemClipboard();
        Map<DataFormat, Object> content = new HashMap<>();
        for (DataFormat format : clip.getContentTypes()) {
            try {
                Object o = clip.getContent(format);
                if (o != null) content.put(format, o);
            } catch (RuntimeException ignored) {
                // a format JavaFX cannot read back
            }
        }
        return content;
    }

    private static Void run(Runnable r) {
        r.run();
        return null;
    }

    private static <T> T fx(Callable<T> task) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(task.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    private static void waitFor(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.call()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for " + what);
            Thread.sleep(100);
        }
    }

    /** Opens a dialog as its button would, types {@code text} in its field, saves a screenshot of it and cancels it. */
    private static void dialogShot(Stage stage, String name, Runnable open, String text) throws Exception {
        Platform.runLater(open);   // it stays in showAndWait() until the dialog closes
        waitFor("the dialog of " + name, () -> fx(() -> dialog(stage) != null));
        Stage dialog = fx(() -> dialog(stage));
        if (!text.isEmpty()) fx(() -> run(() -> ((TextField) dialog.getScene().lookup(".text-field")).setText(text)));
        shot(dialog, name);
        fx(() -> run(() -> {
            DialogPane pane = (DialogPane) dialog.getScene().getRoot().lookup(".dialog-pane");
            pane.getButtonTypes().stream().filter(b -> b.getButtonData().isCancelButton())
                    .forEach(b -> ((Button) pane.lookupButton(b)).fire());
        }));
        waitFor("the dialog to close", () -> fx(() -> dialog(stage) == null));
    }

    private static Stage dialog(Stage owner) {
        return Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getOwner() == owner && s.isShowing())
                .map(Stage.class::cast).findFirst().orElse(null);
    }

    private static void shot(Window window, String name) throws Exception {
        Thread.sleep(500);   // let the WebView paint
        WritableImage image = fx(() -> window.getScene().snapshot(null));
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] argb = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, w, h, argb, 0, w);
        ImageIO.write(png, "png", Files.createDirectories(Path.of("target", "smoke")).resolve(name + ".png").toFile());
    }
}
