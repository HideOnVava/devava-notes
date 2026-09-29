package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Kind;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opens the real window, CodeMirror included, and uses it as a person would minus the keyboard:
 * {@code ./mvnw verify -Dsmoke=true}. Screenshots of each step land in target/smoke. It never
 * presses Copy, which would overwrite the clipboard of whoever runs it.
 */
@EnabledIfSystemProperty(named = "smoke", matches = "true")
class AppSmokeTest {

    private static final String TEMPLATE = "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n}";

    private static final String IDEA = """
            Keep each value's index in a **hash map**; for every `x`, look up `target - x`.

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
            Two even parts only if w is even and greater than 2.
            """;

    private static final String DSU = """
            ---
            kind: snippet
            algorithms: [dsu]
            ---
            ## When to use
            Joining sets and asking whether two items are together.

            ## Code

            ```cpp
            int find(int x) { return p[x] == x ? x : p[x] = find(p[x]); }
            ```
            """;

    private static final String LECTURE = """
            The limit that starts it all: $\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1$, and on its own line

            $$
            f'(x) = \\lim_{h \\to 0} \\frac{f(x + h) - f(x)}{h}
            $$

            """;

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
    void usesAClassNotesNotebook(@TempDir Path home) throws Exception {
        Stage[] window = new Stage[1];
        NotesApp app = open(home, window);
        Stage stage = window[0];
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        NotebookType course = NotebookType.CLASS_NOTES;
        LocalDate today = LocalDate.now();
        Path notebook = fx(() -> app.createNotebook("Calculus II", course));
        fx(() -> run(() -> app.openNotebook(notebook)));

        Path limits = fx(() -> app.createNote(course.kinds().get(0), "Limits", ""));
        fx(() -> run(() -> {
            type(stage, "unit", "1. Limits");
            ((JSObject) engine.executeScript("window")).call("insertText", LECTURE);
        }));
        waitFor("the lecture to save itself", () -> Files.readString(limits).contains("\\frac{\\sin x}{x}"));
        Path homework = fx(() -> app.createNote(course.kinds().get(1), "Homework 3", ""));
        fx(() -> run(() -> ((DatePicker) stage.getScene().lookup("#property-due")).setValue(today.plusDays(1))));
        waitFor("the due date to be saved", () -> Files.readString(homework).contains("due: " + today.plusDays(1)));

        // Written elsewhere: a late assignment, one already done, and an exam.
        Files.writeString(notebook.resolve("Homework 2.md"), "---\nkind: assignment\ndue: " + today.minusDays(3) + "\nstatus: Pending\n---\n");
        Files.writeString(notebook.resolve("Homework 1.md"), "---\nkind: assignment\ndue: " + today.minusDays(9) + "\nstatus: Done\n---\n");
        Files.writeString(notebook.resolve("Midterm.md"), "---\nkind: exam\ndate: " + today.plusDays(5) + "\ntopics: [limits, derivatives]\n---\n");
        fx(() -> run(app::refreshFromDisk));
        fx(() -> run(() -> app.browser.choose("Upcoming", null)));
        shot(stage, "c1-upcoming");

        fx(() -> run(() -> app.setProperty(notebook.resolve("Homework 2.md"), "status", "Done")));   // "Mark as done"
        assertTrue(Files.readString(notebook.resolve("Homework 2.md")).contains("status: Done"));

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
        assertEquals(2, fx(() -> stage.getScene().getRoot().lookupAll(".upcoming-row").size()));   // Homework 3 and the Midterm
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
        Path notebook = fx(() -> app.createNotebook("Algorithms", cp));
        Vault.setMeta(notebook, "cpp", TEMPLATE + "\n");
        fx(() -> run(() -> app.openNotebook(notebook)));

        // A LeetCode link: judge and id filled in, and no C++ main() in its solution.
        Path twoSum = fx(() -> app.createNote(problem, "Two Sum", "https://leetcode.com/problems/two-sum/"));
        String created = Files.readString(twoSum);
        assertTrue(created.startsWith("---\nstatus: To do\ndate: " + LocalDate.now()
                + "\nurl: https://leetcode.com/problems/two-sum/\njudge: LeetCode\nid: two-sum\n---\n"), created);
        assertTrue(created.contains("```cpp\n\n```"), created);
        fx(() -> run(() -> {
            type(stage, "difficulty", "Easy");
            type(stage, "algorithms", "hash map, arrays");
            choose(stage, "status", "Solved");
            ((JSObject) engine.executeScript("window")).call("insertText", IDEA);
        }));
        waitFor("the note to save itself", () -> Files.readString(twoSum).contains("look up `target - x`"));
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

        // Written elsewhere (Obsidian): a problem and a snippet of the Code Library.
        Files.writeString(notebook.resolve("CF 4A - Watermelon.md"), WATERMELON);
        Files.writeString(notebook.resolve("DSU.md"), DSU);
        fx(() -> run(app::refreshFromDisk));
        fx(() -> run(() -> app.insertSnippet(notebook.resolve("DSU.md"))));
        waitFor("the snippet to be saved in the problem", () -> Files.readString(cf).contains("int find(int x)"));
        fx(() -> run(() -> app.browser.choose("All problems", null)));
        shot(stage, "1-problem");

        fx(() -> run(() -> app.browser.choose("By algorithm", "graphs")));
        shot(stage, "2-graphs");
        fx(() -> run(() -> app.browser.choose("By difficulty", null)));
        shot(stage, "3-by-difficulty");
        fx(() -> run(() -> {
            app.browser.choose("All problems", null);
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

        assertEquals(WATERMELON, Files.readString(notebook.resolve("CF 4A - Watermelon.md")));   // only read, never rewritten
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> run(stage::close));
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

    private static void shot(Stage stage, String name) throws Exception {
        Thread.sleep(500);   // let the WebView paint
        WritableImage image = fx(() -> stage.getScene().snapshot(null));
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] argb = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, w, h, argb, 0, w);
        ImageIO.write(png, "png", Files.createDirectories(Path.of("target", "smoke")).resolve(name + ".png").toFile());
    }
}
