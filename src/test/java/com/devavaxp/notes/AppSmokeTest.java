package com.devavaxp.notes;

import javafx.application.Platform;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opens the real window, CodeMirror included, and uses it as a person would minus the keyboard:
 * {@code ./mvnw verify -Dsmoke=true}. Screenshots of each step land in target/smoke.
 */
@EnabledIfSystemProperty(named = "smoke", matches = "true")
class AppSmokeTest {

    private static final String NOTE = """
            # Two Sum

            Keep each value's index in a **hash map**; for every `x`, look up `target - x`.

            ```cpp
            #include <bits/stdc++.h>
            using namespace std;

            int main() {
                int n, target;
                cin >> n >> target;
                unordered_map<int, int> seen;
                for (int i = 0; i < n; i++) {
                    int x;
                    cin >> x;
                    if (seen.count(target - x)) {
                        cout << seen[target - x] << " " << i << "\\n";
                        return 0;
                    }
                    seen[x] = i;
                }
            }
            ```

            - [x] O(n) time, O(n) memory
            - [ ] Try it with two pointers
            """;

    @Test
    void writesSavesAndReadsANote(@TempDir Path home) throws Exception {
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

        Path notebook = fx(() -> app.createNotebook("Algorithms"));
        fx(() -> {
            app.openNotebook(notebook);
            return null;
        });
        Path note = fx(() -> app.createNote("Two Sum"));
        // As if typed: CodeMirror reports the change and the note saves itself.
        fx(() -> ((JSObject) engine.executeScript("window")).call("insertText", NOTE));
        waitFor("the note to save itself", () -> Files.readString(note).equals(NOTE));
        shot(stage, "1-edit");

        fx(() -> {
            app.toggleMode();
            return null;
        });
        waitFor("C++ colored in the reading view", () -> fx(() ->
                ((Number) engine.executeScript("document.querySelectorAll('#reading .tok-keyword').length")).intValue() > 0));
        shot(stage, "2-read");

        fx(() -> {
            app.showHome();
            return null;
        });
        shot(stage, "3-home");
        assertEquals(List.of(), fx(() -> List.copyOf(app.editor.errors)));
        fx(() -> {
            stage.close();
            return null;
        });
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
