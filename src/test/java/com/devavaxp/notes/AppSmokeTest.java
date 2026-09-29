package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Kind;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
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
 * {@code ./mvnw verify -Dsmoke=true}. Screenshots of each step land in target/smoke.
 */
@EnabledIfSystemProperty(named = "smoke", matches = "true")
class AppSmokeTest {

    private static final String IDEA = """
            Keep each value's index in a **hash map**; for every `x`, look up `target - x`.

            ```cpp
            unordered_map<int, int> seen;
            for (int i = 0; i < n; i++) {
                if (seen.count(target - a[i])) return {seen[target - a[i]], i};
                seen[a[i]] = i;
            }
            ```

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

    @Test
    void usesACompetitiveProgrammingNotebook(@TempDir Path home) throws Exception {
        Platform.startup(() -> {
        });
        Platform.setImplicitExit(false);
        NotesApp app = fx(NotesApp::new);
        Stage stage = fx(() -> {
            Stage s = new Stage();
            s.setX(-4000);   // off screen: the test should not get in anyone's way
            app.show(s, new Vault(home));
            return s;
        });
        WebEngine engine = fx(() -> app.editor.view.getEngine());
        waitFor("the editor to load", () -> fx(() -> app.editor.ready));

        NotebookType cp = NotebookType.COMPETITIVE_PROGRAMMING;
        Kind problem = cp.kinds().get(0), snippet = cp.kinds().get(1);
        Path notebook = fx(() -> app.createNotebook("Algorithms", cp));
        fx(() -> run(() -> app.openNotebook(notebook)));
        Path twoSum = fx(() -> app.createNote(problem, "Two Sum"));
        assertTrue(Files.readString(twoSum).startsWith("---\nstatus: To do\ndate: " + LocalDate.now() + "\n---\n## Idea"));

        // Properties through the panel, the text through CodeMirror: the note saves itself.
        fx(() -> run(() -> {
            choose(stage, "judge", "LeetCode");
            type(stage, "id", "1");
            type(stage, "difficulty", "Easy");
            type(stage, "algorithms", "hash map, arrays");
            choose(stage, "status", "Solved");
            ((JSObject) engine.executeScript("window")).call("insertText", IDEA);
        }));
        waitFor("the note to save itself", () -> Files.readString(twoSum).contains("seen[a[i]] = i;"));
        String saved = Files.readString(twoSum);
        assertTrue(saved.startsWith("---\nstatus: Solved\ndate: " + LocalDate.now() + "\njudge: LeetCode\nid: 1\ndifficulty: Easy\n"
                + "algorithms: [hash map, arrays]\n---\n" + IDEA + "## Idea"), saved);

        // A note written elsewhere (Obsidian) shows up when the window comes back, and a snippet.
        Files.writeString(notebook.resolve("CF 4A - Watermelon.md"), WATERMELON);
        fx(() -> run(app::refreshFromDisk));
        fx(() -> app.createNote(snippet, "DSU"));
        fx(() -> run(() -> {
            app.openNote(twoSum);
            app.browser.choose("All problems", null);
        }));
        shot(stage, "1-problem");

        fx(() -> run(() -> app.browser.choose("By algorithm", null)));
        shot(stage, "2-by-algorithm");
        fx(() -> run(() -> {
            app.browser.choose("All problems", null);
            app.browser.showTable(true);
        }));
        shot(stage, "3-table");
        fx(() -> run(() -> {
            app.browser.showTable(false);
            app.toggleMode();
        }));
        waitFor("C++ colored in the reading view", () -> fx(() ->
                ((Number) engine.executeScript("document.querySelectorAll('#reading .tok-keyword').length")).intValue() > 0));
        shot(stage, "4-read");
        fx(() -> run(app::showHome));
        shot(stage, "5-home");

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
