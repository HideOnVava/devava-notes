package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Kind;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
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
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Window;
import netscape.javascript.JSObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    private static final String INHERITANCE = """
            classDiagram
                class Animal {
                    #String nombre
                    +sonido() String
                }
                class Perro {
                    +sonido() String
                }
                class Gato {
                    +sonido() String
                }
                Animal <|-- Perro
                Animal <|-- Gato""";

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

    private static final String COLUMNS = """
            | Columna | Tipo | Restricciones |
            | --- | --- | --- |
            | employee_id | NUMBER(6) | PRIMARY KEY |
            | first_name | VARCHAR2(20) |  |
            | hire_date | DATE | DEFAULT SYSDATE NOT NULL |
            | department_id | NUMBER(4) | REFERENCES departments |""";

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
        Thread.sleep(400);   // its scroll into view, which closes menus, done
        fx(() -> engine.executeScript("[...document.querySelectorAll('.cm-card-head .chip')].at(-1).click()"));
        waitFor("its menu of languages", () -> fx(() -> Boolean.TRUE.equals(engine.executeScript("!!document.querySelector('.lang-menu')"))));
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
        key(app, KeyCode.ENTER);   // the first one found
        waitFor("a Kotlin block", () -> Files.readString(note).contains("```kotlin\nStack<Integer> pila = new Stack<>();\n```\n"));
        fx(() -> engine.executeScript("document.querySelector('.cm-card-head .chip').click()"));
        waitFor("the menu of languages", () -> fx(() -> Boolean.TRUE.equals(engine.executeScript("!!document.querySelector('.lang-menu')"))));
        key(app, KeyCode.ESCAPE);
        waitFor("the menu closed with Esc", () -> fx(() -> Boolean.FALSE.equals(engine.executeScript("!!document.querySelector('.lang-menu')"))));
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
        assertFalse(shown.contains("Libre") || shown.contains("Programación competitiva"), shown.toString());   // Nuevo cuaderno libre's
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
        Path poo = subject(app, "Programación Orientada a Objetos", NotebookType.PROGRAMMING, "java", before);
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
        Path employees = db.resolve("EMPLOYEES.md");
        Files.writeString(employees, "---\nkind: table\n---\n## Columnas\n\n" + COLUMNS + "\n\n## Creación\n\n```plsql\n" + CREATE + "\n```\n");
        Files.writeString(db.resolve("Segundo examen.md"), "---\nkind: exam\ndate: " + today.plusDays(9) + "\ntopics: [plsql/cursores]\n---\n");
        Path partial = calculus.resolve("Derivadas parciales.md");
        Files.writeString(partial, PARTIAL.formatted(today.minusDays(1)));
        Files.writeString(calculus.resolve("Tarea 3.md"), "---\nkind: assignment\ndue: " + today.plusDays(1) + "\nstatus: Pending\n---\n");
        Files.writeString(physics.resolve("Leyes de Newton.md"), "---\ndate: " + today.minusDays(3) + "\nunit: 2. Dinámica\n---\n");
        Path inheritance = poo.resolve("Herencia.md");
        Files.writeString(inheritance, "---\ndate: " + today.minusDays(150) + "\nunit: 2. Herencia y polimorfismo\ntopic: Herencia\n---\n"
                + "## Ideas clave\n\nUna clase hija hereda los atributos y métodos de su clase padre con `extends`, y redefine los que"
                + " necesita.\n\n```mermaid\n" + INHERITANCE + "\n```\n");
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
            app.openNotebook(db);
            app.browser.choose("Tablas", null);
            app.openNote(employees);
        }));
        waitFor("the table's grid", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-table tbody tr').length")).intValue() == 5));
        shot(stage, "readme-tabla");
        fx(() -> run(() -> {
            app.openNotebook(poo);
            app.openNote(inheritance);
        }));
        waitFor("the class diagram", () -> drawn(engine, ".cm-diagram") == 1);
        fx(() -> engine.executeScript("document.querySelector('.cm-diagram').scrollIntoView({block: 'end'})"));
        shot(stage, "readme-diagrama");
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

    /** A note with all that sits between lines of text: headings, pictures, code cards, lists. */
    private static final String MIXED = """
            ## Introducción

            Este es un párrafo largo que debe ocupar más de una línea en el editor, para ver cómo se acomoda el texto cuando se pasa al siguiente renglón, igual que en una nota de clase de verdad.

            ### Ejemplo sencillo
            Aquí el ### intenta alinear todo lo de abajo. Este texto en sí mismo es el ejemplo: una vez llegas al límite de una línea y te pasas a la otra, a veces el texto sale un poco más a la izquierda que el resto.

            ![](<attachments/captura.png>)

            ```java
            class Animal {
                String sonido() { return "..."; }
            }
            ```
            ![](<attachments/salida.png>)

            ## Salida

            ```output
            ¡Guau!
            ```
            ```java
            int x = 1;
            ```

            * Esto es el header de una lista
              * Se supone que esto es un subtítulo del header.
              No se tabula lo suficiente.
            * Un elemento de lista muy largo, que tiene que ocupar más de una línea en el editor para comprobar que lo que se pasa al siguiente renglón empieza donde empieza su texto, y no debajo de su viñeta.

            - [ ] Una tarea

            ## Final
            Última línea.""";

    /**
     * What is drawn is where the cursor goes: a click on any line puts the cursor on it, the arrows
     * go through the lines one by one past headings, pictures, code cards and lists, a click in the
     * gap around a card puts it right before or after the card, and headings and lists line up.
     */
    @Test
    void putsTheCursorWhereItIsClicked(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path notebook = fx(() -> app.createNotebook("Pruebas", NotebookType.CLASS_NOTES));
        Path note = notebook.resolve("Mezcla.md");
        Files.writeString(note, MIXED);
        fx(() -> run(() -> {
            app.openNotebook(notebook);
            app.openNote(note);
        }));
        fx(() -> run(() -> window[0].setHeight(1600)));   // the whole note in sight: CodeMirror draws only what is
        waitFor("the cards", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-card-head').length")).intValue() == 3));
        Thread.sleep(800);
        int lines = fx(() -> ((Number) engine.executeScript("editorView.state.doc.lines")).intValue());

        // CodeMirror's map of the note is where WebKit drew each line.
        assertEquals("", fx(() -> engine.executeScript("""
                [...editorView.contentDOM.querySelectorAll('.cm-line')].map(l => {
                    const drift = Math.round(l.getBoundingClientRect().top - editorView.lineBlockAt(editorView.posAtDOM(l)).top - editorView.documentTop);
                    return drift ? editorView.state.doc.lineAt(editorView.posAtDOM(l)).number + ': ' + drift + 'px' : '';
                }).filter(d => d).join(', ')""")));

        // A click on any line, where it is drawn, puts the cursor on that line.
        List<String> missed = new ArrayList<>();
        for (int n = 1; n <= lines; n++) {
            int line = n;
            String spot = (String) fx(() -> engine.executeScript(("(() => { const l = editorView.state.doc.line(%d), el = editorView.domAtPos(l.from).node; "
                    + "const box = (el.nodeType === 3 ? el.parentNode : el).closest('.cm-line'); if (!box) return ''; "
                    + "const r = box.getBoundingClientRect(); return (r.left + 60) + ',' + (r.top + Math.min(11, r.height / 2)); })()").formatted(line)));
            if (spot.isEmpty()) continue;   // a ``` line: the head or the foot of its card
            String[] xy = spot.split(",");
            click(app.editor.view, Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
            int landed = fx(() -> ((Number) engine.executeScript("editorView.state.doc.lineAt(editorView.state.selection.main.head).number")).intValue());
            if (landed != line) missed.add(line + "→" + landed);
        }
        assertEquals(List.of(), missed, "clicks that put the cursor on another line");

        // Down and Up go through every line of text in order: the ``` lines are the cards' heads and feet.
        List<Integer> text = new ArrayList<>();
        List<String> all = MIXED.lines().toList();
        for (int n = 1; n <= all.size(); n++) if (!all.get(n - 1).startsWith("```")) text.add(n);
        fx(() -> run(app.editor::focus));
        press(app, KeyCode.HOME);
        List<Integer> down = new ArrayList<>(List.of(1)), up = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            key(app, KeyCode.DOWN);
            int at = cursorLine(engine);
            if (at != down.get(down.size() - 1)) down.add(at);
        }
        assertEquals(text, down, "the lines Down goes through");
        up.add(down.get(down.size() - 1));
        for (int i = 0; i < 60; i++) {
            key(app, KeyCode.UP);
            int at = cursorLine(engine);
            if (at != up.get(up.size() - 1)) up.add(at);
        }
        assertEquals(text.reversed(), up, "the lines Up goes through");

        // A heading's text starts where its paragraphs' does; a list steps in, and its lines line up.
        String x = "(p => Math.round(editorView.coordsAtPos(p).left))";
        String start = "(n => editorView.state.doc.line(n).from)";
        String row = "(p => Math.round(editorView.coordsAtPos(editorView.moveToLineBoundary(editorView.state.selection.constructor.cursor(p), false).head).left))";
        assertEquals(fx(() -> engine.executeScript(x + "(" + start + "(3))")), fx(() -> engine.executeScript(x + "(" + start + "(1) + 3)")),
                "the text of ## Introducción and of its paragraph");
        assertEquals(fx(() -> engine.executeScript(x + "(" + start + "(6))")), fx(() -> engine.executeScript(x + "(" + start + "(5) + 4)")),
                "the text of ### Ejemplo sencillo and of its paragraph");
        int outer = ((Number) fx(() -> engine.executeScript(x + "(" + start + "(26) + 2)"))).intValue();
        int inner = ((Number) fx(() -> engine.executeScript(x + "(" + start + "(27) + 4)"))).intValue();
        int more = ((Number) fx(() -> engine.executeScript(x + "(" + start + "(28) + 2)"))).intValue();
        int wrapped = ((Number) fx(() -> engine.executeScript(row + "(" + start + "(30) - 2)"))).intValue();
        int item = ((Number) fx(() -> engine.executeScript(x + "(" + start + "(29) + 2)"))).intValue();
        assertTrue(inner - outer >= 20, "a sub-item steps in: " + outer + " → " + inner);
        assertEquals(inner, more, "a line that continues the sub-item starts where its text does");
        assertEquals(item, wrapped, "what wraps of a long item starts where its text does");
        // A click in the gap above the first card puts the cursor right before it, one below it right after it.
        String[] gaps = ((String) fx(() -> engine.executeScript("""
                (() => { const top = document.querySelector('.cm-card-top').getBoundingClientRect(),
                               bottom = document.querySelector('.cm-card-bottom').getBoundingClientRect();
                         return [top.left + 200, top.top + 4, bottom.left + 200, bottom.bottom - 4].join(','); })()"""))).split(",");
        click(app.editor.view, Double.parseDouble(gaps[0]), Double.parseDouble(gaps[1]));
        assertEquals("10:0", cursor(engine), "a click above the card");
        assertTrue(fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.cm-card-top.here')")));
        shot(window[0], "k1-before-card");
        click(app.editor.view, Double.parseDouble(gaps[2]), Double.parseDouble(gaps[3]));
        assertEquals("14:3", cursor(engine), "a click below the card");
        fx(() -> run(() -> ((JSObject) engine.executeScript("window")).call("insertText", "Entre el código y la imagen")));
        waitFor("the text between the card and the picture", () -> Files.readString(note).contains("}\n```\nEntre el código y la imagen\n![](<attachments/salida.png>)"));
        shot(window[0], "k2-between");

        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(window[0]::close));
    }

    private static int cursorLine(WebEngine engine) throws Exception {
        return fx(() -> ((Number) engine.executeScript("editorView.state.doc.lineAt(editorView.state.selection.main.head).number")).intValue());
    }

    /** The cursor as line:column. */
    private static String cursor(WebEngine engine) throws Exception {
        return fx(() -> (String) engine.executeScript(
                "(() => { const h = editorView.state.selection.main.head, l = editorView.state.doc.lineAt(h); return l.number + ':' + (h - l.from); })()"));
    }

    /** A click of the mouse on a page, at a point of the page: the mouse moves there, and clicks. */
    private static void click(WebView page, double x, double y) throws Exception {
        onScreen(page);
        fx(() -> run(() -> {
            Point2D scene = page.localToScene(x, y), screen = page.localToScreen(x, y);   // a MouseEvent is made in the scene's coordinates
            for (EventType<MouseEvent> type : List.of(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
                boolean moved = type == MouseEvent.MOUSE_MOVED;
                page.fireEvent(new MouseEvent(type, scene.getX(), scene.getY(), screen.getX(), screen.getY(), moved ? MouseButton.NONE : MouseButton.PRIMARY,
                        moved ? 0 : 1, false, false, false, false, type == MouseEvent.MOUSE_PRESSED, false, false, false, false, !moved, null));
            }
        }));
        Thread.sleep(120);
    }

    /** A drawing for a note, made in the app: inserted at the cursor, shown in the reading view, and drawn on again. */
    @Test
    void drawsAPictureForTheNote(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path notebook = fx(() -> app.createNotebook("Estructura de Datos", NotebookType.PROGRAMMING));
        fx(() -> run(() -> app.openNotebook(notebook)));
        Path note = fx(() -> app.createNote(kind(NotebookType.PROGRAMMING, "lecture"), "Pilas y colas", ""));
        fx(() -> run(() -> ((JSObject) engine.executeScript("window")).call("insertText", "Una pila y una cola:\n\n")));

        // The drawing window, with Excalidraw in Spanish.
        fx(() -> run(() -> button(stage, "Dibujo").fire()));
        waitFor("Excalidraw in the drawing window", () -> fx(() -> app.drawing != null && app.drawing.opened || app.drawing != null && !app.drawing.errors.isEmpty()));
        Drawing drawing = fx(() -> app.drawing);
        assertEquals(List.of(), fx(() -> List.copyOf(drawing.errors)));
        WebEngine draw = fx(() -> drawing.view.getEngine());
        Thread.sleep(1500);   // its fonts and its toolbar
        shot(drawing.stage, "x1-drawing-new");
        fx(() -> draw.executeScript("""
                drawingAPI.updateScene({elements: drawingElements([
                    {type: 'rectangle', x: 80, y: 190, width: 220, height: 110, label: {text: 'Pila'}},
                    {type: 'arrow', x: 310, y: 245, width: 160, height: 0},
                    {type: 'ellipse', x: 480, y: 190, width: 200, height: 110, label: {text: 'Cola'}}]), captureUpdate: 'IMMEDIATELY'})"""));
        Thread.sleep(600);
        shot(drawing.stage, "readme-dibujo");
        String count = "drawingAPI.getSceneElements().length", images = "drawingAPI.getSceneElements().filter(e => e.type === 'image').length";
        int drawn = ((Number) fx(() -> draw.executeScript(count))).intValue();

        // Its menu, without links that open nowhere here.
        fx(() -> draw.executeScript("document.querySelector('.dropdown-menu-button').click()"));
        Thread.sleep(300);
        shot(drawing.stage, "x6-drawing-menu");
        String menu = (String) fx(() -> draw.executeScript("document.querySelector('.dropdown-menu').textContent"));
        assertFalse(menu.contains("GitHub") || menu.contains("Discord"), menu);
        fx(() -> draw.executeScript("document.querySelector('.dropdown-menu-button').click()"));

        // Its keyboard shortcuts, which read keys JavaFX's WebKit leaves out (keys.js): a tool's letter, Ctrl+A,
        // Ctrl+Z. And its clipboard, through Java as in the editor: shapes copied and pasted, with Ctrl+V and with
        // Pegar in its menu, and the same for pictures: a screenshot, and an image file.
        double width = fx(() -> drawing.view.getWidth()), height = fx(() -> drawing.view.getHeight());
        click(drawing.view, width / 2, height * 0.7);   // an empty spot of the canvas
        key(drawing.view, KeyCode.R);
        assertEquals("rectangle", fx(() -> draw.executeScript("drawingAPI.getAppState().activeTool.type")));
        key(drawing.view, KeyCode.V);
        assertEquals("selection", fx(() -> draw.executeScript("drawingAPI.getAppState().activeTool.type")));
        Path diagram = home.resolve("diagrama.png");
        BufferedImage green = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 120; x++) for (int y = 0; y < 80; y++) green.setRGB(x, y, 0x2E9E5B);
        ImageIO.write(green, "png", diagram.toFile());
        WritableImage screenshot = new WritableImage(160, 90);
        for (int x = 0; x < 160; x++) for (int y = 0; y < 90; y++) screenshot.getPixelWriter().setArgb(x, y, 0xFF2B4FD8);
        Map<DataFormat, Object> saved = fx(AppSmokeTest::clipboard);
        try {
            press(drawing.view, KeyCode.A);
            press(drawing.view, KeyCode.C);
            assertTrue(fx(() -> Clipboard.getSystemClipboard().getString()).contains("\"rectangle\""), "the shapes on the clipboard");
            press(drawing.view, KeyCode.V);
            waitFor("the shapes pasted", () -> fx(() -> ((Number) draw.executeScript(count)).intValue() == 2 * drawn));
            press(drawing.view, KeyCode.Z);
            waitFor("the paste undone", () -> fx(() -> ((Number) draw.executeScript(count)).intValue() == drawn));
            pasteFromMenu(draw, width / 2, height * 0.7);
            waitFor("the shapes pasted from the menu", () -> fx(() -> ((Number) draw.executeScript(count)).intValue() == 2 * drawn));
            press(drawing.view, KeyCode.Z);
            waitFor("that paste undone", () -> fx(() -> ((Number) draw.executeScript(count)).intValue() == drawn));

            fx(() -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.IMAGE, screenshot)));
            press(drawing.view, KeyCode.V);
            waitFor("the screenshot in the drawing", () -> fx(() -> ((Number) draw.executeScript(images)).intValue() == 1));
            fx(() -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.FILES, List.of(diagram.toFile()))));
            pasteFromMenu(draw, width * 0.25, height * 0.7);
            waitFor("the image file in the drawing", () -> fx(() -> ((Number) draw.executeScript(images)).intValue() == 2));
            Thread.sleep(500);
            assertEquals(2, ((Number) fx(() -> draw.executeScript(images))).intValue(), "each picture once");
            assertEquals(List.of((int) (width / 2), (int) (height * 0.7), (int) (width * 0.25), (int) (height * 0.7)), fx(() -> draw.executeScript("""
                    (s => drawingAPI.getSceneElements().filter(e => e.type === 'image').flatMap(i => [
                        Math.trunc((i.x + i.width / 2 + s.scrollX) * s.zoom.value + s.offsetLeft),
                        Math.trunc((i.y + i.height / 2 + s.scrollY) * s.zoom.value + s.offsetTop)]))(drawingAPI.getAppState())
                    .join()""")).toString().lines().flatMap(l -> Stream.of(l.split(","))).map(Integer::valueOf).toList(),
                    "each picture where the mouse was");
        } finally {
            fx(() -> Clipboard.getSystemClipboard().setContent(saved));
        }
        Thread.sleep(400);
        shot(drawing.stage, "x7-drawing-with-picture");

        // Shapes kept in the library (Añadir a la biblioteca): in a file of the notes' folder, for every drawing.
        fx(() -> draw.executeScript("drawingAPI.updateLibrary({libraryItems: [{id: 'pila', status: 'unpublished', created: 1, "
                + "elements: drawingAPI.getSceneElements().filter(e => e.type === 'rectangle')}], merge: true})"));
        Path library = home.resolve("Biblioteca de dibujos.excalidrawlib");
        waitFor("the library kept", () -> Files.exists(library) && Files.readString(library).contains("\"id\": \"pila\""));
        fx(() -> run(() -> button(drawing.stage, "Insertar en la nota").fire()));
        waitFor("the drawing in the note", () -> Files.readString(note).contains("![](<attachments/Dibujo "));
        Path picture;
        try (Stream<Path> files = Files.list(notebook.resolve("attachments"))) {
            picture = files.filter(Vault::isDrawing).findFirst().orElseThrow();
        }
        byte[] png = Files.readAllBytes(picture);
        assertEquals((byte) 0x89, png[0]);
        assertTrue(new String(png, java.nio.charset.StandardCharsets.ISO_8859_1).contains("application/vnd.excalidraw+json"), "the drawing inside the PNG");
        assertTrue(Files.readString(note).contains("Una pila y una cola:\n\n![](<attachments/" + picture.getFileName() + ">)"), Files.readString(note));
        waitFor("the button to draw on it again", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-draw-edit').length")).intValue() == 1));
        shot(stage, "x5-editing-with-drawing");

        // Read: the drawing shows, and a click opens it to draw on it again.
        fx(() -> run(app::toggleMode));
        waitFor("the drawing shown", () -> fx(() -> ((Number) engine.executeScript(
                "(document.querySelector('#reading img.drawing') || {}).naturalWidth || 0")).intValue() > 400));
        waitFor("the drawing at the size it was drawn", () -> fx(() -> (Boolean) engine.executeScript(
                "(i => i.style.width === i.naturalWidth / 2 + 'px')(document.querySelector('#reading img.drawing'))")));
        shot(stage, "x3-note-with-drawing");
        String filter = "getComputedStyle(document.querySelector('#reading img.drawing')).filter";
        fx(() -> run(() -> app.setDark(true)));
        waitFor("the reading view in dark", () -> fx(() -> "dark".equals(engine.executeScript("document.documentElement.dataset.theme"))));
        assertEquals("none", fx(() -> engine.executeScript(filter)), "a drawing with pictures, as it is");
        shot(stage, "x8-dark-drawing-with-pictures");
        fx(() -> run(() -> app.setDark(false)));
        fx(() -> engine.executeScript("document.querySelector('#reading img.drawing').click()"));
        waitFor("the drawing opened again", () -> fx(() -> app.drawing != drawing && app.drawing != null && app.drawing.opened));
        Drawing again = fx(() -> app.drawing);
        WebEngine redraw = fx(() -> again.view.getEngine());
        assertTrue(((Number) fx(() -> redraw.executeScript("drawingAPI.getSceneElements().length"))).intValue() >= 3);
        fx(() -> redraw.executeScript("drawingAPI.updateLibrary({libraryItems: [], merge: true}).then(items => window.kept = items.map(i => i.id).join())"));
        waitFor("the library in the next drawing", () -> fx(() -> "pila".equals(redraw.executeScript("window.kept"))));
        waitFor("its pictures selected", () -> fx(() -> (Boolean) redraw.executeScript("""
                drawingAPI.updateScene({appState: {selectedElementIds: Object.fromEntries(
                    drawingAPI.getSceneElements().filter(e => e.type === 'image').map(e => [e.id, true]))}}),
                Object.keys(drawingAPI.getAppState().selectedElementIds).length === 2""")));   // Excalidraw selects on its next frame
        key(again.view, KeyCode.DELETE);   // with no click first
        assertEquals(0, ((Number) fx(() -> redraw.executeScript(images))).intValue(), "the pictures deleted with Supr");
        fx(() -> redraw.executeScript("drawingAPI.updateScene({elements: [...drawingAPI.getSceneElements(), "
                + "...drawingElements([{type: 'text', x: 80, y: 340, text: 'LIFO y FIFO'}, {type: 'text', x: 80, y: 390, text: '¿Último en entrar?', fontFamily: 3}])]})"));
        assertEquals(Boolean.TRUE, fx(() -> redraw.executeScript("""
                drawingAPI.getSceneElements().filter(e => e.type === 'text' && !e.containerId).every(e => {
                    const c = document.createElement('canvas').getContext('2d');
                    c.font = e.fontSize + 'px ' + (e.fontFamily === 3 ? 'Cascadia' : 'Excalifont');
                    return Math.abs(e.width - c.measureText(e.text).width) < 1;
                })""")), "texts measured in the fonts they are drawn in");
        long before = Files.getLastModifiedTime(picture).toMillis();
        Thread.sleep(50);
        fx(() -> run(() -> button(again.stage, "Guardar dibujo").fire()));
        waitFor("the drawing saved again", () -> Files.getLastModifiedTime(picture).toMillis() > before);
        waitFor("the reading view with it", () -> fx(() -> (Boolean) engine.executeScript(
                "(document.querySelector('#reading img.drawing') || {}).complete === true")));
        Thread.sleep(400);
        shot(stage, "x4-note-redrawn");
        fx(() -> run(() -> app.setDark(true)));
        waitFor("the drawing turned dark", () -> fx(() -> String.valueOf(engine.executeScript(filter)).contains("invert")));
        shot(stage, "x9-dark-drawing");
        assertEquals(List.of(), fx(() -> List.copyOf(again.errors)));
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /** The links Excalidraw opens in a window of their own (Explorar bibliotecas) go to the browser once, without this computer's paths. */
    @Test
    void opensTheDrawingWindowsLinksInTheBrowser(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        open(home, window);
        Stage stage = window[0];
        List<String> links = new CopyOnWriteArrayList<>();
        Drawing drawing = fx(() -> new Drawing(stage, "Dibujo nuevo", "Insertar en la nota", null, false, home.resolve("b.excalidrawlib"), links::add, png -> {}));
        fx(() -> run(drawing.stage::show));
        waitFor("Excalidraw", () -> fx(() -> drawing.opened));
        WebEngine draw = fx(() -> drawing.view.getEngine());
        fx(() -> draw.executeScript("document.querySelector('.default-sidebar-trigger').click()"));
        waitFor("the library", () -> fx(() -> (Boolean) draw.executeScript("[...document.querySelectorAll('a, button')].some(b => b.textContent === 'Explorar bibliotecas')")));
        fx(() -> draw.executeScript("[...document.querySelectorAll('a, button')].find(b => b.textContent === 'Explorar bibliotecas').click()"));
        waitFor("the link", () -> !links.isEmpty());
        Thread.sleep(1000);
        assertEquals(List.of("https://libraries.excalidraw.com/"), links);
        assertEquals(List.of(), fx(() -> List.copyOf(drawing.errors)));
        fx(() -> run(stage::close));
    }

    /** A note with a table between two paragraphs, its columns aligned as Markdown says. */
    private static final String TABLE = """
            ## Complejidad

            Lo que cuesta cada operación.

            | Estructura | Inserta | Busca |
            | --- | :---: | ---: |
            | Arreglo | O(n) | O(1) |
            | Pila | O(1) | O(n) |

            Texto después.
            """;

    /** A table is written in its cells, as a grid: the keys of a spreadsheet, its bar, and the note keeps Markdown. */
    @Test
    void writesInTheCellsOfATable(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebView page = fx(() -> app.editor.view);
        WebEngine engine = page.getEngine();
        Path notebook = fx(() -> app.createNotebook("Estructura de Datos", NotebookType.PROGRAMMING));
        Path note = notebook.resolve("Tablas.md");
        Files.writeString(note, TABLE);
        fx(() -> run(() -> {
            app.openNotebook(notebook);
            app.openNote(note);
        }));
        waitFor("the grid", () -> fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.cm-table tbody')")));
        Thread.sleep(500);
        shot(stage, "t1-table");
        assertEquals("Estructura|Inserta|Busca/Arreglo|O(n)|O(1)/Pila|O(1)|O(n)", grid(engine));
        assertEquals("left center right", fx(() -> engine.executeScript(
                "[...document.querySelector('.cm-table tbody').rows[1].cells].map(c => getComputedStyle(c).textAlign).join(' ')")));
        // The lines below it are where CodeMirror has them, so clicks land where they show.
        assertEquals("", fx(() -> engine.executeScript("""
                [...editorView.contentDOM.querySelectorAll('.cm-line')].map(l => {
                    const drift = Math.round(l.getBoundingClientRect().top - editorView.lineBlockAt(editorView.posAtDOM(l)).top - editorView.documentTop);
                    return drift ? editorView.state.doc.lineAt(editorView.posAtDOM(l)).number + ': ' + drift + 'px' : '';
                }).filter(d => d).join(', ')""")));

        // A click in a cell writes in it, and the note gets it as it is typed.
        clickCell(page, 1, 0);
        assertEquals("Arreglo", fx(() -> engine.executeScript("document.activeElement.textContent")));
        typeIn(page, " dinámico");
        assertTrue(text(engine).contains("| Arreglo dinámico | O(n) | O(1) |"), text(engine));
        assertEquals("Arreglo dinámico", fx(() -> engine.executeScript("document.activeElement.textContent")), "the cell keeps the focus");

        // Tab goes to the next cell, its text selected; Enter to the one below, and past the last row adds one.
        key(page, KeyCode.TAB);
        typeIn(page, "O(1)");
        assertTrue(text(engine).contains("| Arreglo dinámico | O(1) | O(1) |"), text(engine));
        key(page, KeyCode.ENTER);
        assertEquals("2,1", focused(engine));
        key(page, KeyCode.ENTER);
        assertEquals("3,1", focused(engine));
        assertTrue(text(engine).contains("| Pila | O(1) | O(n) |\n|  |  |  |\n"), text(engine));
        typeIn(page, "x");
        assertTrue(text(engine).contains("|  | x |  |"), text(engine));
        key(page, KeyCode.BACK_SPACE);
        // Enter on that new row, left empty: out of the table, as out of a list.
        key(page, KeyCode.ENTER);
        assertEquals("", focused(engine));
        assertTrue(fx(() -> (Boolean) engine.executeScript("editorView.hasFocus")));
        assertEquals("Estructura|Inserta|Busca/Arreglo dinámico|O(1)|O(1)/Pila|O(1)|O(n)", grid(engine));

        // A | in a cell stays in it: the note escapes it. Ctrl+Z takes back what was typed.
        clickCell(page, 2, 2);
        typeIn(page, " | O(log n)");
        assertTrue(text(engine).contains("| Pila | O(1) | O(n) \\| O(log n) |"), text(engine));
        assertEquals("Estructura|Inserta|Busca/Arreglo dinámico|O(1)|O(1)/Pila|O(1)|O(n) | O(log n)", grid(engine));
        press(page, KeyCode.Z);
        assertTrue(text(engine).contains("| Pila | O(1) | O(n) |\n"), text(engine));
        assertEquals("2,2", focused(engine));

        // The arrows go from cell to cell, and out of the table at its edges.
        key(page, KeyCode.UP);
        assertEquals("1,2", focused(engine));
        key(page, KeyCode.UP);
        key(page, KeyCode.UP);
        assertEquals("", focused(engine));
        assertEquals(4, cursorLine(engine), "the empty line above the table");
        key(page, KeyCode.DOWN);
        assertEquals("0,0", focused(engine));

        // The bar: + Fila below the cell, + Columna after it, and ⋯ for the rest.
        clickCell(page, 1, 0);
        bar(engine, 0, "Fila");
        assertEquals("2,0", focused(engine));
        assertEquals("Estructura|Inserta|Busca/Arreglo dinámico|O(1)|O(1)/||/Pila|O(1)|O(n)", grid(engine));
        bar(engine, 0, "Columna");
        assertEquals("2,1", focused(engine));
        assertTrue(text(engine).contains("| Estructura |  | Inserta | Busca |\n| --- | --- | :---: | ---: |"), text(engine));
        typeIn(page, "Borra");
        shot(stage, "t2-table-new-row-and-column");
        menu(engine, 0, "Quitar la columna 2");
        assertTrue(text(engine).contains("| Estructura | Inserta | Busca |\n| --- | :---: | ---: |"), text(engine));
        menu(engine, 0, "Quitar la fila 2");
        assertEquals("Estructura|Inserta|Busca/Arreglo dinámico|O(1)|O(1)/Pila|O(1)|O(n)", grid(engine));
        clickCell(page, 1, 1);
        menu(engine, 0, "Alinear a la derecha");
        assertTrue(text(engine).contains("| --- | ---: | ---: |"), text(engine));
        assertEquals("right", fx(() -> engine.executeScript("getComputedStyle(document.querySelector('.cm-table tbody').rows[2].cells[1]).textAlign")));

        // Copiar: a table for Word, Excel or Docs, and its Markdown for the rest.
        Map<DataFormat, Object> saved = fx(AppSmokeTest::clipboard);
        try {
            bar(engine, 0, "Copiar");
            waitFor("the table on the clipboard", () -> fx(() -> Clipboard.getSystemClipboard().hasHtml()));
            String html = fx(() -> Clipboard.getSystemClipboard().getHtml());
            assertTrue(html.contains("<table border=\"1\"") && html.contains("<td align=\"right\">O(1)</td>"), html);
            assertTrue(fx(() -> Clipboard.getSystemClipboard().getString()).startsWith("| Estructura | Inserta | Busca |\n| --- | ---: |"));
        } finally {
            fx(() -> Clipboard.getSystemClipboard().setContent(saved));
        }

        // What Buscar finds in a cell is marked, and selected in that cell: what is typed then replaces it there.
        fx(() -> run(() -> app.editor.selectMatch("Pila")));
        assertEquals("Pila", fx(() -> engine.executeScript("document.querySelector('.cm-table .found')?.textContent ?? ''")));
        waitFor("the cell found", () -> "2,0".equals(focused(engine)));
        assertEquals("Pila", fx(() -> engine.executeScript("getSelection().toString()")));
        typeIn(page, "Cola");
        assertEquals("Estructura|Inserta|Busca/Arreglo dinámico|O(1)|O(1)/Cola|O(1)|O(n)", grid(engine));
        assertTrue(text(engine).contains("| Cola | O(1) | O(n) |\n"), text(engine));

        // In Vista previa, as aligned; in the dark, the same grid.
        fx(() -> run(app::toggleMode));
        waitFor("the table read", () -> fx(() -> (Boolean) engine.executeScript("!!document.querySelector('#reading table')")));
        assertEquals("left right right / left right right", fx(() -> engine.executeScript("[...document.querySelectorAll('#reading tr')].slice(0, 2)"
                + ".map(r => [...r.cells].map(c => getComputedStyle(c).textAlign.replace('-webkit-', '').replace('start', 'left')).join(' ')).join(' / ')")));
        shot(stage, "t3-table-read");
        fx(() -> run(() -> {
            app.toggleMode();
            app.setDark(true);
        }));
        waitFor("the editor in dark", () -> fx(() -> "dark".equals(engine.executeScript("document.documentElement.dataset.theme"))));
        shot(stage, "t4-table-dark");
        fx(() -> run(() -> app.setDark(false)));

        fx(() -> run(app::save));
        assertEquals(text(engine), Files.readString(note));
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /**
     * A table starts from Tabla in the bar (as many columns and rows as chosen), from cells pasted from
     * a spreadsheet, or from its header typed and Enter; Java's buttons and pastes go in its cells.
     */
    @Test
    void makesTables(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebView page = fx(() -> app.editor.view);
        WebEngine engine = page.getEngine();
        Path notebook = fx(() -> app.createNotebook("Fundamentos de Programación", NotebookType.PROGRAMMING));
        Path note = notebook.resolve("Prueba de escritorio.md");
        Files.writeString(note, """
                Una prueba de escritorio:

                - Una lista con una tabla, que se queda como texto:

                  | a | b |
                  | --- | --- |
                  | 1 | 2 |

                Datos pegados a una tabla:
                | x | y |
                | --- | --- |
                | 1 | 2 |

                Calificaciones:""");
        fx(() -> run(() -> {
            app.openNotebook(notebook);
            app.openNote(note);
        }));
        waitFor("the grid", () -> fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.cm-table tbody')")));
        assertEquals(1, tableCount(engine), "the table in the list is text");

        // Delete at the end of a line right before a table goes into its first cell, not its Markdown.
        fx(() -> engine.executeScript("editorView.focus(); editorView.dispatch({selection: {anchor: editorView.state.doc.line(9).to}})"));
        key(page, KeyCode.DELETE);
        assertEquals("0,0", focused(engine));
        assertTrue(text(engine).contains("Datos pegados a una tabla:\n| x | y |"), text(engine));

        // Tabla, in the bar: its squares say how many columns and rows, and one is made at the cursor.
        fx(() -> engine.executeScript("editorView.focus(); editorView.dispatch({selection: {anchor: editorView.state.doc.line(1).to}})"));
        fx(() -> run(() -> button(stage, "Tabla").fire()));
        waitFor("the sizes", () -> fx(() -> Window.getWindows().stream().anyMatch(w -> w instanceof ContextMenu && w.isShowing())));
        Window sizes = fx(() -> Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).findFirst().orElseThrow());
        Node square = fx(() -> sizes.getScene().getRoot().lookupAll(".size-square").stream()
                .filter(n -> GridPane.getColumnIndex(n) == 2 && GridPane.getRowIndex(n) == 2).findFirst().orElseThrow());
        mouse(square, MouseEvent.MOUSE_ENTERED);
        assertTrue(fx(() -> texts(sizes)).contains("3 columnas × 3 filas"), fx(() -> texts(sizes)).toString());
        shot(sizes, "t5-table-sizes");
        mouse(square, MouseEvent.MOUSE_CLICKED);
        waitFor("the new table", () -> tableCount(engine) == 2);
        assertEquals("0,0", focused(engine));
        assertTrue(text(engine).startsWith("Una prueba de escritorio:\n\n|  |  |  |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |\n\n- Una lista"), text(engine));
        typeIn(page, "Paso");
        key(page, KeyCode.TAB);
        typeIn(page, "i");
        key(page, KeyCode.TAB);
        typeIn(page, "suma");
        key(page, KeyCode.TAB);
        typeIn(page, "1");
        key(page, KeyCode.TAB);
        typeIn(page, "0");
        key(page, KeyCode.TAB);
        typeIn(page, "0");
        assertTrue(text(engine).contains("| Paso | i | suma |\n| --- | --- | --- |\n| 1 | 0 | 0 |\n|  |  |  |\n"), text(engine));

        // Negrita in the bar, with a cell being written in: in that cell.
        fx(() -> engine.executeScript("getSelection().selectAllChildren(document.activeElement)"));
        fx(() -> run(() -> app.editor.format("bold")));
        assertTrue(text(engine).contains("| 1 | 0 | **0** |"), text(engine));
        assertEquals("1,2", focused(engine));

        Map<DataFormat, Object> saved = fx(AppSmokeTest::clipboard);
        try {
            // A screenshot pasted in a cell: its link goes in that cell.
            fx(() -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.IMAGE, new WritableImage(3, 2))));
            fx(() -> engine.executeScript("(c => { const r = document.createRange(); r.selectNodeContents(c); r.collapse(false); "
                    + "getSelection().removeAllRanges(); getSelection().addRange(r); })(document.activeElement)"));   // the caret at the cell's end
            press(page, KeyCode.V);
            waitFor("the picture in the cell", () -> Pattern.compile("\\| 1 \\| 0 \\| \\*\\*0\\*\\*!\\[]\\(<attachments/Imagen pegada [^>]+\\.png>\\) \\|\n")
                    .matcher(text(engine)).find());
            assertEquals("1,2", focused(engine));

            // Cells from a spreadsheet, pasted in a cell: from there on, adding the rows that do not fit.
            clickCell(page, 1, 1);
            fx(() -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, "7\t8\n9\t10\n11\t12\n")));
            press(page, KeyCode.V);
            assertEquals("Paso|i|suma/1|7|8/|9|10/|11|12", grid(engine));

            // Pasted outside a table, they make one, the first row its header; Ctrl+Z leaves what was pasted.
            fx(() -> engine.executeScript("editorView.focus(); editorView.dispatch({selection: {anchor: editorView.state.doc.length}})"));
            fx(() -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, "Nombre\tCalificación\r\nAna\t95\r\nLuis\t88\r\n")));
            press(page, KeyCode.V);
            waitFor("the pasted table", () -> tableCount(engine) == 3);
            assertTrue(text(engine).endsWith("Calificaciones:\n\n| Nombre | Calificación |\n| --- | --- |\n| Ana | 95 |\n| Luis | 88 |"), text(engine));
            shot(stage, "t6-table-pasted");
            press(page, KeyCode.Z);
            assertTrue(text(engine).endsWith("Calificaciones:Nombre\tCalificación\nAna\t95\nLuis\t88\n"), text(engine));
            press(page, KeyCode.Z);
            assertTrue(text(engine).endsWith("Calificaciones:"), text(engine));
        } finally {
            fx(() -> Clipboard.getSystemClipboard().setContent(saved));
        }

        // A header typed, then Enter: the table to fill in, from its first row.
        fx(() -> engine.executeScript("editorView.dispatch({changes: {from: editorView.state.doc.length, insert: '\\n\\n'}, "
                + "selection: {anchor: editorView.state.doc.length + 2}})"));
        typeIn(page, "| i | x | x < 3 |");
        key(page, KeyCode.ENTER);
        assertEquals("1,0", focused(engine));
        assertTrue(text(engine).endsWith("Calificaciones:\n\n| i | x | x < 3 |\n| --- | --- | --- |\n|  |  |  |"), text(engine));

        // ↑ from the text right below a table goes into its last row; Esc leaves it, below.
        int below = fx(() -> ((Number) engine.executeScript("editorView.state.doc.line(8).from")).intValue());
        fx(() -> engine.executeScript("editorView.focus(); editorView.dispatch({selection: {anchor: " + below + "}})"));
        assertEquals("", fx(() -> engine.executeScript("editorView.state.doc.line(8).text")), "the empty line below the first table");
        key(page, KeyCode.UP);
        assertEquals("3,0", focused(engine));
        key(page, KeyCode.ESCAPE);
        assertEquals("", focused(engine));
        assertEquals(8, cursorLine(engine));
        shot(stage, "t7-tables");

        fx(() -> run(app::save));
        assertEquals(text(engine), Files.readString(note));
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /**
     * Renaming a note takes its [[links]] along: in the notes whose link meant it, in its notebook
     * and in others, the open one too (read in Vista previa, its link found); not where another
     * notebook has its own note by the old name, and not where the new name leads to another note,
     * which the user is told.
     */
    @Test
    void renamingANoteKeepsItsLinks(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path ed = fx(() -> app.createNotebook("Estructura de Datos", NotebookType.PROGRAMMING));
        Path physics = fx(() -> app.createNotebook("Física", NotebookType.CLASS_NOTES));
        Path ideas = fx(() -> app.createNotebook("Ideas", NotebookType.GENERAL));
        Path other = fx(() -> app.createNotebook("Otra materia", NotebookType.CLASS_NOTES));
        Path stacks = Files.writeString(ed.resolve("Pilas.md"), "Una pila: [[Pilas]] soy yo.\n");
        Path review = Files.writeString(ed.resolve("Repaso.md"), "Ver [[Pilas]] y [[pilas|la pila]], no `[[Pilas]]`.\r\n");
        Files.writeString(physics.resolve("Pilas y colas.md"), "Las de Física.\n");
        Path lab = Files.writeString(physics.resolve("Lab.md"), "En el lab, [[Pilas]].\n");
        Path idea = Files.writeString(ideas.resolve("Proyecto.md"), "Usa [[Pilas]].\n");
        Path locked = Files.writeString(ideas.resolve("Bloqueada.md"), "También [[Pilas]].\n");
        Files.setAttribute(locked, "dos:readonly", true);   // as a note another program holds
        Files.write(ideas.resolve("Latin.md"), "Ni un enlace, y en Latin-1: ñ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        Files.writeString(other.resolve("Pilas.md"), "Otras pilas.\n");
        Path own = Files.writeString(other.resolve("Tarea.md"), "Sobre [[Pilas]] de esta materia.\n");
        fx(() -> run(() -> {
            app.openNotebook(ed);
            app.openNote(review);
            app.toggleMode();
        }));
        waitFor("the note read", () -> fx(() -> "Pilas".equals(engine.executeScript("document.querySelector('#reading a.wikilink')?.dataset.note"))));

        Platform.runLater(() -> app.renameNote(stacks));   // it stays in showAndWait() until the dialogs close
        waitFor("the dialog to rename", () -> fx(() -> dialog(stage) != null));
        Stage dialog = fx(() -> dialog(stage));
        fx(() -> run(() -> {
            ((TextField) dialog.getScene().lookup(".text-field")).setText("Pilas y colas");
            ((Button) ((DialogPane) dialog.getScene().getRoot().lookup(".dialog-pane")).lookupButton(ButtonType.OK)).fire();
        }));
        // Física's [[Pilas]] stays: there [[Pilas y colas]] is Física's own note. The user is told.
        waitFor("what is told", () -> fx(() -> dialog(stage) != null && texts(dialog(stage)).contains("Algunos enlaces siguen como [[Pilas]]")));
        Stage told = fx(() -> dialog(stage));
        String said = fx(() -> String.join(" ", texts(told)));
        assertTrue(said.contains("En “Lab”, [[Pilas y colas]] llevaría a otra nota que se llama igual: ahí quedó [[Pilas]]."), said);
        assertTrue(said.contains("No se pudieron cambiar en “Bloqueada”: cámbialos a [[Pilas y colas]] a mano."), said);
        assertFalse(said.contains("Latin"), "a note with no such link is not mentioned, even one it cannot read: " + said);
        shot(told, "h5-links-kept");
        fx(() -> run(() -> ((Button) ((DialogPane) told.getScene().getRoot().lookup(".dialog-pane")).lookupButton(ButtonType.OK)).fire()));

        waitFor("the note renamed", () -> fx(() -> Files.exists(ed.resolve("Pilas y colas.md")) && dialog(stage) == null));
        assertEquals("Ver [[Pilas y colas]] y [[Pilas y colas|la pila]], no `[[Pilas]]`.\r\n", Files.readString(review));
        assertEquals("Una pila: [[Pilas y colas]] soy yo.\n", Files.readString(ed.resolve("Pilas y colas.md")), "its own link to itself");
        assertEquals("Usa [[Pilas y colas]].\n", Files.readString(idea));
        assertEquals("En el lab, [[Pilas]].\n", Files.readString(lab), "where the new title is another note's");
        assertEquals("También [[Pilas]].\n", Files.readString(locked), "the note that could not be written");
        Files.setAttribute(locked, "dos:readonly", false);
        assertEquals("Sobre [[Pilas]] de esta materia.\n", Files.readString(own), "the [[Pilas]] of a notebook with its own");
        // The open note, read: its links lead to the renamed note, which is there.
        waitFor("the note read again", () -> fx(() -> (Boolean) engine.executeScript(
                "(a => a.dataset.note === 'Pilas y colas' && !a.classList.contains('missing'))(document.querySelector('#reading a.wikilink'))")));
        fx(() -> run(app::toggleMode));
        assertTrue(text(engine).startsWith("Ver [[Pilas y colas]]"), "the open note, as it is now: " + text(engine));

        // Back to the old name, and nothing is left to tell.
        Path renamed = ed.resolve("Pilas y colas.md");
        Platform.runLater(() -> app.renameNote(renamed));
        waitFor("the dialog to rename", () -> fx(() -> dialog(stage) != null));
        Stage again = fx(() -> dialog(stage));
        fx(() -> run(() -> {
            ((TextField) again.getScene().lookup(".text-field")).setText("Pilas");
            ((Button) ((DialogPane) again.getScene().getRoot().lookup(".dialog-pane")).lookupButton(ButtonType.OK)).fire();
        }));
        waitFor("the old name back", () -> fx(() -> Files.exists(ed.resolve("Pilas.md")) && dialog(stage) == null));
        assertEquals("Usa [[Pilas]].\n", Files.readString(idea));
        assertEquals("En el lab, [[Pilas]].\n", Files.readString(lab));

        // A name a [[link]] cannot hold, [[Pilas [1]]] being one to "Pilas [1": the links stay, and the user is told.
        Platform.runLater(() -> app.renameNote(ed.resolve("Pilas.md")));
        waitFor("the dialog to rename", () -> fx(() -> dialog(stage) != null));
        Stage bracket = fx(() -> dialog(stage));
        fx(() -> run(() -> {
            ((TextField) bracket.getScene().lookup(".text-field")).setText("Pilas [1]");
            ((Button) ((DialogPane) bracket.getScene().getRoot().lookup(".dialog-pane")).lookupButton(ButtonType.OK)).fire();
        }));
        waitFor("what is told", () -> fx(() -> dialog(stage) != null && texts(dialog(stage)).contains("Algunos enlaces siguen como [[Pilas]]")));
        Stage cannot = fx(() -> dialog(stage));
        assertTrue(fx(() -> String.join(" ", texts(cannot))).contains("Un [[enlace]] no puede llevar a “Pilas [1]”"));
        fx(() -> run(() -> ((Button) ((DialogPane) cannot.getScene().getRoot().lookup(".dialog-pane")).lookupButton(ButtonType.OK)).fire()));
        waitFor("that rename done", () -> fx(() -> Files.exists(ed.resolve("Pilas [1].md")) && dialog(stage) == null));
        assertEquals("Usa [[Pilas]].\n", Files.readString(idea));

        // A newer version on GitHub: its button in the bar.
        fx(() -> run(() -> app.showUpdate(new Updates.Release("9.9.9", Updates.RELEASES + "tag/v9.9.9"))));
        assertTrue(fx(() -> texts(stage)).contains("Nueva versión 9.9.9"));
        shot(stage, "h3-update");
        fx(() -> run(app::showHome));
        assertTrue(fx(() -> texts(stage)).contains("Nueva versión 9.9.9"), "on Home too");
        shot(stage, "h4-update-home");
        fx(() -> run(() -> app.setDark(true)));
        shot(stage, "h6-update-dark");
        fx(() -> run(() -> app.setDark(false)));

        // Ayuda says the version and leads to Reportar un problema.
        Platform.runLater(app::showHelp);
        waitFor("Ayuda", () -> fx(() -> dialog(stage) != null));
        assertTrue(fx(() -> texts(dialog(stage))).contains("Devava Notes dev · devava XP Studios"));
        fx(() -> run(() -> ((Button) ((DialogPane) dialog(stage).getScene().getRoot().lookup(".dialog-pane")).lookupButton(ButtonType.CLOSE)).fire()));
        waitFor("Ayuda closed", () -> fx(() -> dialog(stage) == null));
        dialogShot(stage, "h1-help", app::showHelp, "");
        dialogShot(stage, "h2-report", app::reportProblem, "");
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
    }

    /**
     * Exportar a PDF: the note as Vista previa shows it (its code in color, its formula, its table, its
     * drawing at its size), without what is only clicked; the PDF opens once made, and a note of the
     * list goes to paper as well as the open one.
     */
    @Test
    void exportsANoteToPdf(@TempDir Path home, @TempDir Path out) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        Path ed = fx(() -> app.createNotebook("Estructura de Datos", NotebookType.PROGRAMMING));
        // A drawing as the app saves it: a PNG twice the size it shows at
        Path drawing = Files.createDirectories(ed.resolve("attachments")).resolve("Dibujo 2026-10-15 10.00.00.excalidraw.png");
        ImageIO.write(new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB), "png", drawing.toFile());
        StringBuilder longCode = new StringBuilder();
        for (int i = 1; i <= 60; i++) longCode.append("        System.out.println(\"Paso ").append(i).append(" de la prueba de escritorio\");\n");
        Path note = Files.writeString(ed.resolve("Sesión 5 · Pilas.md"), STACK.formatted("2026-10-15").replace("## Notas", """
                ## Complejidad

                | Operación | Pila | Cola |
                | --- | :---: | :---: |
                | Insertar | O(1) | O(1) |
                | Buscar | O(n) | O(n) |

                Para derivar respecto a $x$:

                $$
                \\frac{\\partial f}{\\partial x} = \\lim_{h \\to 0} \\frac{f(x + h, y) - f(x, y)}{h}
                $$

                - [x] Entender `push` y `pop`
                - [ ] Practicar con [[Colas]] y #pilas, y leer [la documentación](https://docs.oracle.com/en/java/)

                ![](<attachments/Dibujo 2026-10-15 10.00.00.excalidraw.png>)

                ```mermaid
                flowchart LR
                    push --> pila[(Pila)] --> pop
                ```

                ## Un bloque largo

                ```java
                public class Larga {
                    public static void main(String[] args) {
                """ + longCode + """
                    }
                }
                ```

                ## Notas"""));
        fx(() -> run(() -> app.openNotebook(ed)));
        Path homework = fx(() -> app.createNote(kind(NotebookType.PROGRAMMING, "assignment"), "Práctica 3", ""));
        fx(() -> run(() -> app.setProperty(homework, "due", "2026-10-20")));
        fx(() -> run(() -> app.openNote(note)));   // Práctica 3 saves itself as it is left
        assertEquals("Estructura de Datos · Sesión de código · Unidad 3. Estructuras lineales · 15 de octubre de 2026",
                fx(() -> app.about(Note.parse(Files.readString(note)))));
        assertEquals("Estructura de Datos · Tarea · Entrega: 20 de octubre de 2026", fx(() -> app.about(Note.parse(Files.readString(homework)))));
        assertTrue(fx(() -> app.noteMenu(homework).getItems().stream().map(MenuItem::getText).toList())
                .containsAll(List.of("Exportar a PDF…", "Imprimir…")));

        // What goes on paper: Vista previa's page, with nothing to click
        CompletableFuture<String> printable = new CompletableFuture<>();
        fx(() -> run(() -> app.editor.printable(app.editor.text(), ed, printable::complete)));
        String html = printable.get(15, TimeUnit.SECONDS);
        assertTrue(html.contains("class=\"katex-display\"") && html.contains("class=\"katex\""), "the formulas");
        assertTrue(html.contains("class=\"tok-keyword\""), "the code's colors");
        assertTrue(html.contains("class=\"code-card output\""), "the output's card");
        assertFalse(html.contains("class=\"copy\""), "a Copy button");
        assertTrue(html.contains("<a class=\"wikilink missing\" data-note=\"Colas\">Colas</a>"), "a [[link]] that leads nowhere: " + html);
        assertTrue(html.contains(" href=\"https://docs.oracle.com/en/java/\">la documentación</a>"), "a link to the web, kept");
        assertTrue(html.contains("<span class=\"tag\" data-tag=\"pilas\">#pilas</span>"), "the tag");
        assertTrue(html.contains("class=\"drawing\" style=\"width: 200px;\""), "the drawing at half its PNG: " + html);
        assertTrue(html.contains("<div class=\"diagram\"><div class=\"diagram-drawing\"><svg"), "the diagram, drawn");
        assertFalse(html.contains("title="), "a hint to click the drawing");

        assumeTrue(Pdf.edge(System::getenv) != null, "Microsoft Edge is not installed");
        MenuButton printer = fx(() -> (MenuButton) stage.getScene().getRoot().lookupAll(".menu-button").stream()
                .filter(m -> "Exportar a PDF o imprimir".equals(m.getAccessibleText())).findFirst().orElseThrow());
        Path pdf = out.resolve("Sesión 5 · Pilas.pdf");
        CompletableFuture<Path> opened = new CompletableFuture<>();
        fx(() -> run(() -> app.exportPdf(note, pdf, opened::complete)));
        assertEquals("Creando PDF…", fx(printer::getText));
        assertTrue(fx(printer::isDisabled));
        shot(stage, "pdf-creating");
        assertEquals(pdf, opened.get(60, TimeUnit.SECONDS));
        assertTrue(Files.readString(pdf, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
        waitFor("the printer button back", () -> fx(() -> printer.getText() == null && !printer.isDisabled()));
        Files.copy(pdf, Files.createDirectories(Path.of("target", "smoke")).resolve("pdf-nota.pdf"), StandardCopyOption.REPLACE_EXISTING);

        // A note of the list, not the open one
        Path other = out.resolve("Práctica 3.pdf");
        CompletableFuture<Path> made = new CompletableFuture<>();
        fx(() -> run(() -> app.exportPdf(homework, other, made::complete)));
        assertEquals(other, made.get(60, TimeUnit.SECONDS));
        String practice = Files.readString(other, StandardCharsets.ISO_8859_1);
        assertTrue(practice.contains("/Title <FEFF" + HexFormat.of().withUpperCase().formatHex("Práctica 3".getBytes(StandardCharsets.UTF_16BE)) + ">"));
        assertEquals(note, fx(app::openPath), "the open note stays open");
        fx(() -> run(stage::close));
    }

    /**
     * A diagram (```mermaid): drawn below its code while it is written, and again as it changes; while
     * its code has an error, its last drawing faded and what is wrong. Diagrama starts one; Vista previa
     * shows them alone, in the window's theme, and paper in the light one. Mermaid loads only once a
     * note has a diagram.
     */
    @Test
    void drawsDiagrams(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path poo = fx(() -> app.createNotebook("Programación Orientada a Objetos", NotebookType.PROGRAMMING));
        Path note = Files.writeString(poo.resolve("Herencia.md"), """
                ## Clases

                ```mermaid
                classDiagram
                    Animal <|-- Perro
                ```

                ## El algoritmo

                ```mermaid
                flowchart TD
                    A[Inicio] --> B{¿n > 0?}
                ```
                """);
        fx(() -> run(() -> app.openNotebook(poo)));
        assertEquals("undefined", fx(() -> engine.executeScript("typeof window.mermaid")), "Mermaid, before a note has a diagram");
        fx(() -> run(() -> app.openNote(note)));
        waitFor("the diagrams below their code", () -> drawn(engine, ".cm-diagram") == 2);
        assertEquals(List.of("Diagrama", "Diagrama"), fx(() -> List.of(((String) engine.executeScript(
                "[...document.querySelectorAll('.cm-card-head .chip')].map(c => c.textContent).join('\\n')")).split("\n"))));
        shot(stage, "diagram-1-writing");

        // An error as it is typed: the last drawing stays, faded, and says what is wrong; fixed, it is drawn again
        String end = "B{¿n > 0?}";
        fx(() -> engine.executeScript("(v => v.dispatch({changes: {from: v.state.doc.toString().indexOf('" + end + "') + " + end.length()
                + ", insert: '\\n    B -- Sí -->'}, userEvent: 'input.type'}))(window.editorView)"));
        waitFor("what is wrong with it", () -> fx(() -> (Boolean) engine.executeScript(
                "!!document.querySelector('.cm-diagram .diagram.broken .diagram-error')")));
        String wrong = fx(() -> (String) engine.executeScript("document.querySelector('.diagram.broken .diagram-error').textContent"));
        assertTrue(wrong.startsWith("Error en la línea 4:\n...0?}    B -- Sí -->\n") && !wrong.contains("Expecting"), wrong);   // at the end of the code
        assertTrue(fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.diagram.broken .diagram-drawing svg')")), "the last drawing");
        shot(stage, "diagram-2-error");
        fx(() -> engine.executeScript("(v => v.dispatch({changes: {from: v.state.doc.toString().indexOf('B -- Sí -->') + 11, insert: ' C[Fin]'},"
                + " userEvent: 'input.type'}))(window.editorView)"));
        waitFor("the diagram drawn again", () -> fx(() -> (Boolean) engine.executeScript(
                "!document.querySelector('.diagram.broken') && [...document.querySelectorAll('.cm-diagram svg')].some(s => s.textContent.includes('Fin'))")));

        // Diagrama: a flowchart to start from, after the block with the cursor
        fx(() -> run(() -> app.editor.insertDiagram(NotesApp.DIAGRAMS.get(0).code())));
        waitFor("the new diagram drawn", () -> drawn(engine, ".cm-diagram") == 3);
        waitFor("the note to save itself", () -> Files.readString(note).contains("```mermaid\nflowchart TD\n    inicio([Inicio]) --> leer[/Leer n/]"));

        // Vista previa: the diagrams alone, in the window's theme
        fx(() -> run(app::toggleMode));
        waitFor("the diagrams of Vista previa", () -> drawn(engine, "#reading") == 3);
        assertTrue(fx(() -> (Boolean) engine.executeScript("!document.querySelector('#reading pre, #reading .code-card')")), "code in Vista previa");
        String first = "document.querySelector('#reading .diagram svg').innerHTML";
        assertTrue(fx(() -> (String) engine.executeScript(first)).contains("#E7ECFF"), "the light colors");
        shot(stage, "diagram-3-reading");
        fx(() -> run(() -> app.setDark(true)));
        waitFor("the diagrams drawn dark", () -> fx(() -> (String) engine.executeScript(first)).contains("#232B52"));
        shot(stage, "diagram-4-dark");

        // On paper, light whatever the window's theme
        CompletableFuture<String> printable = new CompletableFuture<>();
        fx(() -> run(() -> app.editor.printable(app.editor.text(), poo, printable::complete)));
        String paper = printable.get(15, TimeUnit.SECONDS);
        assertEquals(3, paper.split("<div class=\"diagram-drawing\"><svg", -1).length - 1, paper);
        assertTrue(paper.contains("#E7ECFF") && !paper.contains("#232B52"), "light on paper");
        assertFalse(paper.contains("data-code"), "the code of the diagrams, twice");
        fx(() -> run(() -> app.setDark(false)));

        // A diagram whose code has an error shows it in Vista previa, above its code
        fx(() -> run(app::toggleMode));
        fx(() -> engine.executeScript("(v => { const at = v.state.doc.toString().indexOf(' Perro');"
                + " v.dispatch({changes: {from: at, to: at + 6}, userEvent: 'delete'}); })(window.editorView)"));   // Animal <|-- (to nothing)
        fx(() -> run(app::toggleMode));
        waitFor("the broken diagram in Vista previa", () -> fx(() -> (Boolean) engine.executeScript(
                "!!document.querySelector('#reading .diagram.broken .diagram-error + .code-card')")), () -> fx(() -> (String) engine.executeScript(
                "document.getElementById('reading').innerHTML.replace(/<svg[\\s\\S]*?<\\/svg>/g, '<svg/>')")));
        assertEquals(2, drawn(engine, "#reading"));
        assertTrue(fx(() -> app.editor.errors).isEmpty(), () -> "editor.js: " + app.editor.errors);
        fx(() -> run(stage::close));
    }

    /** A note of the list goes to paper with its diagram drawn to its size, even with no note open (no editor on screen). */
    @Test
    void diagramsOnPaperWithNoNoteOpen(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Path fp = fx(() -> app.createNotebook("Fundamentos de Programación", NotebookType.PROGRAMMING));
        fx(() -> run(() -> app.openNotebook(fp)));
        assertEquals(null, fx(app::openPath));
        CompletableFuture<String> printable = new CompletableFuture<>();
        fx(() -> run(() -> app.editor.printable("```mermaid\n" + NotesApp.DIAGRAMS.get(0).code() + "\n```\n", fp, printable::complete)));
        String paper = printable.get(15, TimeUnit.SECONDS);
        var width = Pattern.compile("max-width: ([\\d.]+)px").matcher(paper);
        assertTrue(width.find() && Double.parseDouble(width.group(1)) > 100, paper.substring(0, Math.min(paper.length(), 400)));
        assertTrue(paper.contains(">Leer n<") && paper.contains(">Fin<"), "the texts of the diagram");
        fx(() -> run(window[0]::close));
    }

    /**
     * Ctrl+ and Ctrl− (and Ctrl and the wheel) make the note's text bigger and smaller, Ctrl+0 as it
     * was; the size stays for the next time, and a click still lands where it is pointed.
     */
    @Test
    void textSizeStepsAndIsRemembered(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path ideas = fx(() -> app.createNotebook("Ideas", NotebookType.GENERAL));
        Path note = Files.writeString(ideas.resolve("Lista.md"), "Primera línea\nSegunda línea\nTercera línea\nCuarta línea\n");
        fx(() -> run(() -> {
            app.openNotebook(ideas);
            app.openNote(note);
        }));
        press(app, KeyCode.EQUALS);
        assertEquals(1.1, fx(() -> app.editor.view.getZoom()), 1e-9);
        assertTrue(fx(() -> texts(window[0])).contains("Texto al 110 %"), "how big, for a moment");
        press(app, KeyCode.ADD);
        press(app, KeyCode.ADD);
        assertEquals(1.5, fx(() -> app.editor.view.getZoom()), 1e-9);
        press(app, KeyCode.MINUS);
        assertEquals(1.25, fx(() -> app.editor.view.getZoom()), 1e-9);
        assertTrue(Files.readString(home.resolve("settings.json")).contains("\"textSize\": \"1.25\""), Files.readString(home.resolve("settings.json")));

        // At 125 %, a click on the third line puts the cursor there
        String[] xy = fx(() -> (String) engine.executeScript("(v => { const c = v.coordsAtPos(v.state.doc.line(3).from + 4); return c.left + ',' + (c.top + c.bottom) / 2; })(editorView)")).split(",");
        click(app.editor.view, Double.parseDouble(xy[0]) * 1.25, Double.parseDouble(xy[1]) * 1.25);
        assertEquals(3, cursorLine(engine));

        // Ctrl and the wheel: a step a notch
        fx(() -> run(() -> app.editor.view.fireEvent(new ScrollEvent(ScrollEvent.SCROLL, 300, 300, 300, 300, false, true, false, false, false, false,
                0, -40, 0, -40, ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null))));
        assertEquals(1.1, fx(() -> app.editor.view.getZoom()), 1e-9);
        press(app, KeyCode.DIGIT0);
        assertEquals(1.0, fx(() -> app.editor.view.getZoom()), 1e-9);
        press(app, KeyCode.EQUALS);
        fx(() -> run(window[0]::close));

        // The next time, as it was left
        Stage[] again = new Stage[1];
        NotesApp reopened = open(home, again);
        assertEquals(1.1, fx(() -> reopened.editor.view.getZoom()), 1e-9);
        fx(() -> run(again[0]::close));
    }

    /**
     * Callouts, as Obsidian writes them (> [!definition] …): a box of their kind's color with their icon
     * and their name, or their own title, while writing and in Vista previa; Spanish words too, and an
     * unknown one as a note named as written. Recuadro makes one, around the selected lines too.
     */
    @Test
    void writesCallouts(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path ed = fx(() -> app.createNotebook("Estructura de Datos", NotebookType.PROGRAMMING));
        Path note = Files.writeString(ed.resolve("Pilas.md"), """
                > [!definition]
                > Una **pila** guarda sus datos uno sobre otro: sale primero el último en entrar.

                > [!warning] Cuidado con **pop**
                > En una pila vacía, falla.

                > [!advertencia]
                > En español también.

                > [!ojo]
                > Una palabra que no conoce.

                > Una cita, sin más.

                Texto suelto
                """);
        fx(() -> run(() -> {
            app.openNotebook(ed);
            app.openNote(note);
        }));
        waitFor("the callouts while writing", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-callout-mark').length")).intValue() == 4));
        assertEquals("Definición|Advertencia|Advertencia|Ojo", fx(() -> engine.executeScript(
                "[...document.querySelectorAll('.cm-callout-mark')].map(m => m.textContent).join('|')")));
        assertEquals("definition,definition,warning,warning,warning,warning,note,note", fx(() -> engine.executeScript(
                "[...document.querySelectorAll('.cm-line.cm-callout')].map(l => l.dataset.callout).join(',')")));
        shot(stage, "callouts-1-writing");

        // Recuadro: a new one to write in, after the text; and one around a selected line
        fx(() -> engine.executeScript("editorView.dispatch({selection: {anchor: editorView.state.doc.length}})"));
        fx(() -> run(() -> app.editor.insertCallout("example")));
        fx(() -> run(() -> app.editor.insert("Un caso de pila")));
        assertTrue(text(engine).contains("Texto suelto\n> [!example]\n> Un caso de pila"), text(engine));
        fx(() -> engine.executeScript("(v => { const at = v.state.doc.toString().indexOf('Texto suelto'); v.dispatch({selection: {anchor: at, head: at + 5}}); })(editorView)"));
        fx(() -> run(() -> app.editor.insertCallout("important")));
        assertTrue(text(engine).contains("> [!important]\n> Texto suelto\n\n> [!example]"), "kept apart from the next one: " + text(engine));

        // In a quote, a new one goes after it, apart
        fx(() -> engine.executeScript("(v => v.dispatch({selection: {anchor: v.state.doc.toString().indexOf('sin más')}}))(editorView)"));
        fx(() -> run(() -> app.editor.insertCallout("definition")));
        assertTrue(text(engine).contains("> Una cita, sin más.\n\n> [!definition]\n> \n"), text(engine));
        fx(() -> run(() -> app.editor.insert("Cola: FIFO")));

        // Vista previa: boxes with their titles, the content kept; a plain quote stays a quote
        fx(() -> run(app::toggleMode));
        waitFor("the callouts of Vista previa", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading blockquote.callout').length")).intValue() == 7));
        assertEquals("definition:Definición|warning:Cuidado con pop|warning:Advertencia|note:Ojo|definition:Definición|important:Importante|example:Ejemplo",
                fx(() -> engine.executeScript("[...document.querySelectorAll('#reading blockquote.callout')].map(q => q.dataset.callout + ':'"
                        + " + q.querySelector('.callout-title').textContent).join('|')")));
        assertTrue(fx(() -> (String) engine.executeScript("document.querySelector('#reading blockquote.callout p').innerHTML"))
                .startsWith("Una <strong>pila</strong> guarda"), "the definition's text");
        assertEquals(1, fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading blockquote:not(.callout)').length")).intValue()));
        shot(stage, "callouts-2-reading");
        CompletableFuture<String> printable = new CompletableFuture<>();
        fx(() -> run(() -> app.editor.printable(app.editor.text(), ed, printable::complete)));
        assertTrue(printable.get(15, TimeUnit.SECONDS).contains("<blockquote class=\"callout\" data-callout=\"definition\">"), "on paper");
        assertTrue(fx(() -> app.editor.errors).isEmpty(), () -> "editor.js: " + app.editor.errors);
        fx(() -> run(stage::close));
    }

    /**
     * Notas que enlazan aquí: at the end of a note, while writing and in Vista previa, the notes whose
     * [[links]] lead to it, from any notebook but one with a note of that title of its own; a click
     * opens one, and one written elsewhere shows up once the window is back.
     */
    @Test
    void showsTheNotesThatLinkHere(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path ed = fx(() -> app.createNotebook("Estructura de Datos", NotebookType.PROGRAMMING));
        Path poo = fx(() -> app.createNotebook("Programación Orientada a Objetos", NotebookType.PROGRAMMING));
        Path other = fx(() -> app.createNotebook("Otra materia", NotebookType.CLASS_NOTES));
        Path stack = Files.writeString(ed.resolve("Pilas.md"), "## Pilas\n\nLIFO.\n");
        Files.writeString(ed.resolve("Colas.md"), "Como las [[Pilas]], pero FIFO.\n");
        Files.writeString(ed.resolve("Recursividad.md"), "Cada llamada va a [[pilas|la pila]].\n");
        Files.writeString(ed.resolve("Código.md"), "`[[Pilas]]` en código no enlaza.\n");
        Path inheritance = Files.writeString(poo.resolve("Herencia.md"), "Ver [[Pilas]].\n");
        Files.writeString(other.resolve("Pilas.md"), "Las de esta materia.\n");
        Files.writeString(other.resolve("Uso.md"), "[[Pilas]] de aquí.\n");
        fx(() -> run(() -> {
            app.openNotebook(ed);
            app.openNote(stack);
        }));
        waitFor("the notes that link here", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-backlinks .backlink').length")).intValue() == 3));
        assertEquals("Colas|Recursividad|HerenciaProgramación Orientada a Objetos", fx(() -> engine.executeScript(
                "[...document.querySelectorAll('.cm-backlinks .backlink')].map(b => b.textContent).join('|')")));
        shot(stage, "backlinks-1-writing");
        fx(() -> run(app::toggleMode));
        waitFor("them in Vista previa", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .backlinks .backlink').length")).intValue() == 3));
        shot(stage, "backlinks-2-reading");
        fx(() -> run(app::toggleMode));

        // One more, written elsewhere, once the window is back
        Files.writeString(ed.resolve("Árboles.md"), "Un recorrido usa una [[Pilas|pila]].\n");
        fx(() -> run(app::refreshFromDisk));
        waitFor("the new one", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-backlinks .backlink').length")).intValue() == 4));

        // A click opens it, in its notebook
        fx(() -> engine.executeScript("[...document.querySelectorAll('.cm-backlinks .backlink')].find(b => b.textContent.startsWith('Herencia')).click()"));
        waitFor("Herencia, open", () -> inheritance.equals(fx(app::openPath)));
        waitFor("its own (none)", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-backlinks .backlink').length")).intValue() == 0));
        fx(() -> run(stage::close));
    }

    /** [[ in a table's cell offers the titles too: ↑↓ to choose, Enter or Tab to take one, Esc to close them and stay in the cell. */
    @Test
    void linksNotesFromTableCells(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        WebView page = fx(() -> app.editor.view);
        Path ed = fx(() -> app.createNotebook("Estructura de Datos", NotebookType.PROGRAMMING));
        Files.writeString(ed.resolve("Pilas.md"), "");
        Files.writeString(ed.resolve("Pila de llamadas.md"), "");
        Files.writeString(ed.resolve("Colas.md"), "");
        Path note = Files.writeString(ed.resolve("Repaso.md"), "| Tema | Nota |\n| --- | --- |\n|  |  |\n");
        fx(() -> run(() -> {
            app.openNotebook(ed);
            app.openNote(note);
        }));
        waitFor("the grid", () -> tableCount(engine) == 1);
        clickCell(page, 1, 0);
        typeIn(page, "[[pi");
        waitFor("the titles", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cell-links .option').length")).intValue() == 2));
        assertEquals("Pila de llamadas|Pilas", fx(() -> engine.executeScript("[...document.querySelectorAll('.cell-links .option')].map(o => o.textContent).join('|')")));
        shot(stage, "cell-links");
        key(page, KeyCode.DOWN);
        key(page, KeyCode.ENTER);
        assertEquals("[[Pilas]]", fx(() -> engine.executeScript("document.querySelector('.cm-table tbody').rows[1].cells[0].textContent")));
        assertEquals("1,0", focused(engine), "still in the cell");
        assertFalse(fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.cell-links')")));
        typeIn(page, " y [[co");
        waitFor("Colas", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cell-links .option').length")).intValue() == 1));
        key(page, KeyCode.TAB);
        assertTrue(text(engine).contains("| [[Pilas]] y [[Colas]] |"), text(engine));
        assertEquals("1,0", focused(engine), "Tab took the title, it did not move on");
        typeIn(page, " [[pi");
        waitFor("the titles again", () -> fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.cell-links')")));
        key(page, KeyCode.ESCAPE);
        assertFalse(fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.cell-links')")));
        assertEquals("1,0", focused(engine), "Esc closed them, the cell keeps the caret");
        assertTrue(fx(() -> app.editor.errors).isEmpty(), () -> "editor.js: " + app.editor.errors);
        fx(() -> run(stage::close));
    }

    /**
     * Fórmula: every key of its palette draws (KaTeX reads all they write), and one writes in the formula
     * with the cursor or in a new one, the cursor in its first blank; the formula shows drawn below it
     * while it is written, or what is wrong with it.
     */
    @Test
    void writesFormulasFromThePalette(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path calculus = fx(() -> app.createNotebook("Cálculo Vectorial", NotebookType.CLASS_NOTES));
        StringBuilder keys = new StringBuilder();
        int count = 0;
        for (List<NotesApp.MathKey> section : NotesApp.MATH.values()) {
            for (NotesApp.MathKey k : section) {
                keys.append("$").append(k.latex().replace("‸", "x")).append("{}$ ");
                count++;
            }
        }
        Path all = Files.writeString(calculus.resolve("Teclas.md"), keys + "\n");
        Path note = Files.writeString(calculus.resolve("Derivadas.md"), "La derivada de $x^2$ es $2x$.\n\nTexto: \n");
        fx(() -> run(() -> {
            app.openNotebook(calculus);
            app.openNote(all);
            app.toggleMode();
        }));
        int keyCount = count;
        waitFor("every key drawn", () -> fx(() -> ((Number) engine.executeScript("document.querySelectorAll('#reading .katex').length")).intValue() == keyCount));
        assertEquals("", fx(() -> engine.executeScript("[...document.querySelectorAll('#reading .katex-error')].map(e => e.textContent).join(' | ')")),
                "keys KaTeX cannot read");
        fx(() -> run(() -> {
            app.toggleMode();
            app.openNote(note);
        }));

        // In a new formula, the cursor in its first blank
        fx(() -> engine.executeScript("editorView.dispatch({selection: {anchor: editorView.state.doc.toString().indexOf('Texto: ') + 7}})"));
        fx(() -> run(() -> app.editor.insertMath("\\frac{‸}{}")));
        fx(() -> run(() -> app.editor.insert("1")));
        assertTrue(text(engine).contains("Texto: $\\frac{1}{}$"), text(engine));
        // In the formula with the cursor: a letter after it does not join the command's name
        fx(() -> engine.executeScript("editorView.dispatch({selection: {anchor: editorView.state.doc.toString().indexOf('x^2') + 3}})"));
        fx(() -> run(() -> app.editor.insertMath("\\cdot")));
        fx(() -> run(() -> app.editor.insert("y")));
        assertTrue(text(engine).contains("$x^2\\cdot{}y$"), text(engine));
        fx(() -> engine.executeScript("editorView.dispatch({selection: {anchor: editorView.state.doc.toString().indexOf('2x') + 1}})"));
        fx(() -> run(() -> app.editor.insertMath("\\alpha")));
        assertTrue(text(engine).contains("$2\\alpha x$"), text(engine));

        // The palette's own key, through its button
        Button formula = fx(() -> button(stage, "Fórmula"));
        fx(() -> run(formula::fire));
        Window palette = fx(() -> Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).findFirst().orElseThrow());
        shot(palette, "formula-palette");
        fx(() -> run(() -> palette.getScene().getRoot().lookupAll(".math-key").stream().map(Button.class::cast)
                .filter(b -> b.getText().equals("√x")).findFirst().orElseThrow().fire()));
        assertTrue(text(engine).contains("$2\\alpha \\sqrt{}x$"), text(engine));
        fx(() -> run(palette::hide));

        // Drawn while written; what is wrong, when it is
        waitFor("the formula drawn below it", () -> fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.cm-formula .katex')")));
        shot(stage, "formula-preview");
        fx(() -> engine.executeScript("(v => { const at = v.state.doc.toString().indexOf('\\\\frac{1}{}') + 6; v.dispatch({changes: {from: at, to: at + 1, insert: '{'}, selection: {anchor: at + 1}}); })(editorView)"));
        waitFor("what is wrong", () -> fx(() -> (Boolean) engine.executeScript("!!document.querySelector('.cm-formula.wrong')")));
        assertEquals("Falta cerrar una llave }", fx(() -> engine.executeScript("document.querySelector('.cm-formula.wrong .said').textContent")));
        shot(stage, "formula-wrong");
        fx(() -> engine.executeScript("editorView.dispatch({selection: {anchor: 0}})"));
        waitFor("no formula, no drawing", () -> fx(() -> (Boolean) engine.executeScript("!document.querySelector('.cm-formula')")));
        assertTrue(fx(() -> app.editor.errors).isEmpty(), () -> "editor.js: " + app.editor.errors);
        fx(() -> run(stage::close));
    }

    /**
     * A note's pictures while writing: below their line, at their size; a size of their menu or their
     * corner dragged writes Obsidian's |width after their words, which Vista previa and paper follow.
     */
    @Test
    void resizesPicturesWhileWriting(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        Path physics = fx(() -> app.createNotebook("Física General", NotebookType.CLASS_NOTES));
        Path attachments = Files.createDirectories(physics.resolve("attachments"));
        BufferedImage board = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 300; x++) for (int y = 0; y < 150; y++) board.setRGB(x, y, x < 150 ? 0x2B4FD8 : 0x0F8A80);
        ImageIO.write(board, "png", attachments.resolve("pizarrón 1.png").toFile());
        ImageIO.write(new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB), "png", attachments.resolve("Dibujo 2026-10-16 10.00.00.excalidraw.png").toFile());
        Path note = Files.writeString(physics.resolve("Cinemática.md"), """
                ## El pizarrón

                ![](<attachments/pizarrón 1.png>)

                ![](<attachments/Dibujo 2026-10-16 10.00.00.excalidraw.png>)

                `![](<attachments/pizarrón 1.png>)` en código no se muestra.
                """);
        fx(() -> run(() -> {
            app.openNotebook(physics);
            app.openNote(note);
        }));
        waitFor("the pictures, loaded", () -> fx(() -> (Boolean) engine.executeScript(
                "(p => p.length === 2 && [...p].every(i => i.complete && i.naturalWidth > 0))(document.querySelectorAll('.cm-picture img'))")));
        waitFor("the drawing at half its PNG", () -> "200px".equals(fx(() -> engine.executeScript("document.querySelectorAll('.cm-picture-box')[1].style.width"))));
        shot(stage, "pictures-1-writing");

        // A size from its menu, then its corner dragged, then the original
        fx(() -> engine.executeScript("[...document.querySelector('.cm-picture-sizes').children].find(b => b.textContent === 'Mediana').click()"));
        waitFor("|400", () -> text(engine).contains("![|400](<attachments/pizarrón 1.png>)"));
        waitFor("it at 400", () -> "400px".equals(fx(() -> engine.executeScript("document.querySelector('.cm-picture-box').style.width"))));
        fx(() -> engine.executeScript("""
                (h => {
                  const r = h.getBoundingClientRect(), x = r.left + 6, y = r.top + 6;
                  h.dispatchEvent(new MouseEvent('mousedown', {clientX: x, clientY: y, bubbles: true, cancelable: true}));
                  document.dispatchEvent(new MouseEvent('mousemove', {clientX: x + 50, clientY: y, bubbles: true}));
                  document.dispatchEvent(new MouseEvent('mouseup', {clientX: x + 50, clientY: y, bubbles: true}));
                })(document.querySelector('.cm-picture-handle'))"""));
        waitFor("|450", () -> text(engine).contains("![|450](<attachments/pizarrón 1.png>)"));
        fx(() -> engine.executeScript("[...document.querySelector('.cm-picture-sizes').children].find(b => b.textContent === 'Original').click()"));
        waitFor("the original", () -> text(engine).contains("\n![](<attachments/pizarrón 1.png>)\n"));
        fx(() -> engine.executeScript("[...document.querySelector('.cm-picture-sizes').children].find(b => b.textContent === 'Chica').click()"));
        waitFor("|200", () -> text(engine).contains("![|200](<attachments/pizarrón 1.png>)"));

        // Vista previa and paper follow it
        fx(() -> run(app::toggleMode));
        waitFor("Vista previa", () -> "200px".equals(fx(() -> engine.executeScript("(i => i && i.style.width)(document.querySelector('#reading img'))"))));
        assertEquals("", fx(() -> engine.executeScript("document.querySelector('#reading img').alt")), "the size is not words");
        CompletableFuture<String> printable = new CompletableFuture<>();
        fx(() -> run(() -> app.editor.printable(app.editor.text(), physics, printable::complete)));
        assertTrue(printable.get(15, TimeUnit.SECONDS).contains("style=\"width: 200px;\""), "on paper");
        assertTrue(fx(() -> app.editor.errors).isEmpty(), () -> "editor.js: " + app.editor.errors);
        fx(() -> run(stage::close));
    }

    /** How many diagrams are drawn in a part of the editor's page. */
    private static int drawn(WebEngine engine, String where) throws Exception {
        return fx(() -> ((Number) engine.executeScript("document.querySelectorAll('" + where + " .diagram .diagram-drawing svg').length")).intValue());
    }

    private static int tableCount(WebEngine engine) throws Exception {
        return fx(() -> ((Number) engine.executeScript("document.querySelectorAll('.cm-table').length")).intValue());
    }

    /** The mouse on a node, as JavaFX sends it. */
    private static void mouse(Node node, EventType<MouseEvent> type) throws Exception {
        fx(() -> run(() -> node.fireEvent(new MouseEvent(type, 4, 4, 0, 0, MouseButton.PRIMARY, type == MouseEvent.MOUSE_CLICKED ? 1 : 0,
                false, false, false, false, false, false, false, false, false, true, null))));
        Thread.sleep(300);
    }

    /**
     * Home shows this school period's subjects, and apart from them the free notebooks, which have no
     * period; the earlier subjects fold away until asked for.
     */
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
        // Home's cards are in a scroll pane, whose content is there once a pulse has drawn it.
        waitFor("what free notebooks are for, while there are none", () -> fx(() -> texts(stage)).containsAll(List.of("Cuadernos libres",
                "Nuevo cuaderno libre", "Para lo que no es de una materia: ideas, proyectos, la residencia…")));
        fx(() -> app.createNotebook("Ideas", NotebookType.GENERAL));
        Path contests = fx(() -> app.createNotebook("Concursos", NotebookType.COMPETITIVE_PROGRAMMING));
        Vault.setMeta(contests, "period", before.toString());   // as 2.0 let any notebook have one
        fx(() -> run(app::showHome));
        shot(stage, "p1-home-periods");
        List<String> shown = fx(() -> texts(stage));
        assertTrue(shown.containsAll(List.of(now.toString(), "1 materia", "Programación Orientada a Objetos", "Cuadernos libres", "2 cuadernos",
                "Ideas", "Concursos", "Semestres anteriores · 1 materia")), shown.toString());
        assertFalse(shown.contains("Fundamentos de Programación"));
        assertTrue(fx(() -> y(stage, now.toString()) < y(stage, "Cuadernos libres") && y(stage, "Cuadernos libres") < y(stage, "Semestres anteriores · 1 materia")));
        // Only a subject has a period to change.
        assertFalse(fx(() -> menu(card(stage, "Ideas"))).contains("Cambiar periodo…"));
        assertTrue(fx(() -> menu(card(stage, "Programación Orientada a Objetos"))).contains("Cambiar periodo…"));

        // Nuevo cuaderno libre: a name, and a type that is no subject.
        Platform.runLater(app::newFreeNotebook);   // it stays in showAndWait() until the dialog closes
        waitFor("the dialog of Nuevo cuaderno libre", () -> fx(() -> dialog(stage) != null));
        Stage dialog = fx(() -> dialog(stage));
        List<String> offered = fx(() -> texts(dialog));
        assertTrue(offered.containsAll(List.of("Libre", "Programación competitiva")) && !offered.contains("Materia"), offered.toString());
        fx(() -> run(() -> ((TextField) dialog.getScene().lookup(".text-field")).setText("Residencia Profesional")));
        shot(dialog, "p3-new-free-notebook");
        fx(() -> run(() -> button(dialog, "Crear cuaderno").fire()));
        Path residency = home.resolve("Residencia Profesional");
        waitFor("the free notebook", () -> Files.isDirectory(residency) && Vault.type(residency) == NotebookType.GENERAL);
        assertEquals("", Vault.meta(residency, "period"));
        fx(() -> run(app::showHome));
        waitFor("the new free notebook on Home", () -> fx(() -> texts(stage)).containsAll(List.of("Residencia Profesional", "3 cuadernos", "1 materia")));

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

    /** The note as the editor has it now. */
    private static String text(WebEngine engine) throws Exception {
        return fx(() -> (String) engine.executeScript("editorView.state.doc.toString()"));
    }

    /** The first table's grid as shown: its rows, / between them, and | between cells. */
    private static String grid(WebEngine engine) throws Exception {
        return fx(() -> (String) engine.executeScript(
                "[...document.querySelector('.cm-table tbody').rows].map(r => [...r.cells].map(c => c.textContent).join('|')).join('/')"));
    }

    /** The cell being written in, as row,column; "" when it is no cell. */
    private static String focused(WebEngine engine) throws Exception {
        return fx(() -> (String) engine.executeScript(
                "(c => c.matches('th, td') ? c.parentElement.rowIndex + ',' + c.cellIndex : '')(document.activeElement)"));
    }

    /** A click near the end of a cell of the first table, where its text ends. */
    private static void clickCell(WebView page, int row, int col) throws Exception {
        onScreen(page);   // where the cell is drawn once the page has its size
        String[] xy = fx(() -> (String) page.getEngine().executeScript(("(() => { const c = document.querySelector('.cm-table tbody').rows[%d].cells[%d]; "
                + "c.scrollIntoView({block: 'nearest'}); const r = c.getBoundingClientRect(); return (r.right - 6) + ',' + (r.top + r.height / 2); })()")
                .formatted(row, col))).split(",");
        click(page, Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
    }

    /** A button of the bar of the nth table, by what it says. */
    private static void bar(WebEngine engine, int table, String label) throws Exception {
        fx(() -> engine.executeScript(("[...document.querySelectorAll('.cm-table')[%d].querySelectorAll('.cm-table-head button')]"
                + ".find(b => b.textContent === '%s').click()").formatted(table, label)));
        Thread.sleep(150);
    }

    /** An option of the ⋯ menu of the nth table. */
    private static void menu(WebEngine engine, int table, String option) throws Exception {
        fx(() -> engine.executeScript("document.querySelectorAll('.cm-table')[%d].querySelector('.more').click()".formatted(table)));
        Thread.sleep(150);
        fx(() -> engine.executeScript("[...document.querySelectorAll('.table-menu .option')].find(o => o.querySelector('.name').textContent === '%s').click()"
                .formatted(option)));
        Thread.sleep(150);
    }

    /** Text typed on the keyboard, a key at a time. */
    private static void typeIn(WebView page, String text) throws Exception {
        onScreen(page);
        fx(() -> run(() -> {
            for (char ch : text.toCharArray()) {
                String s = String.valueOf(ch);
                KeyCode code = ch == ' ' ? KeyCode.SPACE : Character.isLetterOrDigit(ch) && ch < 128
                        ? KeyCode.getKeyCode(s.toUpperCase()) : KeyCode.UNDEFINED;
                page.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
                page.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, s, "", KeyCode.UNDEFINED, false, false, false, false));
                page.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
            }
        }));
        Thread.sleep(200);
    }

    /** The card of a notebook on Home. */
    private static Button card(Stage stage, String name) {
        return stage.getScene().getRoot().lookupAll(".card").stream().map(Button.class::cast)
                .filter(c -> c.getGraphic().lookupAll(".card-title").stream().anyMatch(t -> name.equals(((Labeled) t).getText())))
                .findFirst().orElseThrow();
    }

    /** What a card's menu offers. */
    private static List<String> menu(Button card) {
        return card.getContextMenu().getItems().stream().map(MenuItem::getText).toList();
    }

    /** Where in the window the label or button that says this is, from the top. */
    private static double y(Stage stage, String text) {
        Deque<Node> nodes = new ArrayDeque<>(List.of(stage.getScene().getRoot()));
        while (!nodes.isEmpty()) {
            Node node = nodes.pop();
            if (node instanceof Labeled l && text.equals(l.getText())) return node.localToScene(0, 0).getY();
            if (node instanceof Parent p) nodes.addAll(p.getChildrenUnmodifiable());
        }
        throw new AssertionError("Nothing says " + text);
    }

    /** The button of the window that says this. */
    private static Button button(Stage stage, String text) {
        return stage.getScene().getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    /** A key, alone, as the keyboard sends it to the editor. */
    private static void key(NotesApp app, KeyCode key) throws Exception {
        key(app.editor.view, key);
    }

    /** A key, alone, as the keyboard sends it to a page. */
    private static void key(WebView page, KeyCode key) throws Exception {
        onScreen(page);
        fx(() -> run(() -> {
            page.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
            page.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, false, false, false, false));
        }));
        Thread.sleep(150);
    }

    /** Pegar, in the menu Excalidraw opens with a right click at a point of its canvas. */
    private static void pasteFromMenu(WebEngine draw, double x, double y) throws Exception {
        fx(() -> draw.executeScript("(c => { for (const type of ['pointermove', 'contextmenu']) c.dispatchEvent(new PointerEvent(type, "
                + "{bubbles: true, cancelable: true, button: type === 'contextmenu' ? 2 : -1, clientX: " + x + ", clientY: " + y + "})); })"
                + "(document.querySelector('.excalidraw__canvas.interactive'))"));
        waitFor("Excalidraw's menu", () -> fx(() -> (Boolean) draw.executeScript("!!document.querySelector('.context-menu')")));
        fx(() -> draw.executeScript("[...document.querySelectorAll('.context-menu-item__label')].find(l => l.textContent === 'Pegar').closest('button').click()"));
        Thread.sleep(300);
    }

    /** Ctrl + the key, as the keyboard sends it to a page. */
    private static void press(WebView page, KeyCode key) throws Exception {
        onScreen(page);
        fx(() -> run(() -> {
            page.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, true, false, false));
            page.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, false, true, false, false));
        }));
        Thread.sleep(300);
    }

    /** Ctrl + the key, as the keyboard sends it to the editor. */
    private static void press(NotesApp app, KeyCode key) throws Exception {
        press(app.editor.view, key);
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
        waitFor(what, condition, () -> "");
    }

    /**
     * Until the page is in a window: a pane the app has just shown gets there only on the next pulse, and keys
     * or clicks sent before then miss the scene's filters.
     */
    private static void onScreen(WebView page) throws Exception {
        waitFor("the page in the window", () -> fx(() -> page.getScene() != null && page.localToScreen(0, 0) != null));
    }

    /** As waitFor, saying what there was instead when it times out. */
    private static void waitFor(String what, Callable<Boolean> condition, Callable<String> instead) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.call()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for " + what + " " + instead.call());
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
