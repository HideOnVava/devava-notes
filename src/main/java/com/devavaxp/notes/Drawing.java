package com.devavaxp.notes;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import netscape.javascript.JSObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The drawing window: Excalidraw (draw.html), in a WebView of its own over the main window. It opens
 * a drawing, a .excalidraw.png that holds its own scene, or a new one, and hands back the drawing as
 * such a PNG for the note. Its page reports to Java as alert() text, as the editor's does ("ready",
 * "opened", "png:base64", "copy:text", "paste", "library:json", "error:message"), and asks for the
 * clipboard's text with prompt(): the clipboard goes through Java here too, since JavaFX's WebKit has
 * no clipboard API and empties the clipboard when a page writes to it. The personal library of
 * shapes is a file Java reads for the page, and writes when it changes.
 */
final class Drawing {

    final Stage stage = new Stage();
    final WebView view = new WebView();
    /** What draw.js reported as failing (also printed to stderr). */
    final List<String> errors = new ArrayList<>();
    private final WebEngine engine = view.getEngine();
    private final byte[] png;
    private final boolean dark;
    private final Path library;
    private final Consumer<byte[]> onDone;
    /** The page has the drawing: from then on, closing it may lose changes. */
    boolean opened;

    /**
     * {@code png}: the drawing to open, or null for a new one. {@code action}: what its button does
     * ("Insertar en la nota", "Guardar dibujo"); {@code onDone} gets the drawing then, unless it is empty.
     * {@code onLink} opens the links Excalidraw opens in a window of their own (its help, its libraries).
     */
    Drawing(Window owner, String title, String action, byte[] png, boolean dark, Path library, Consumer<String> onLink, Consumer<byte[]> onDone) {
        this.png = png;
        this.dark = dark;
        this.library = library;
        this.onDone = onDone;
        Label name = new Label(title);
        name.getStyleClass().add("crumb");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Button picture = new Button("Imagen", Icons.of("image", 15)), cancel = new Button("Cancelar"), done = new Button(action);
        picture.getStyleClass().add("flat");
        picture.setOnAction(e -> choosePicture());
        cancel.getStyleClass().add("flat");
        done.getStyleClass().add("primary");
        cancel.setOnAction(e -> cancel());
        done.setOnAction(e -> {
            if (opened) call("exportDrawing");
        });
        HBox bar = new HBox(8, name, grow, picture, cancel, done);
        bar.getStyleClass().add("bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        BorderPane root = new BorderPane(view, bar, null, null, null);
        if (dark) root.getStyleClass().add("dark");
        Scene scene = new Scene(root, Math.max(900, owner.getWidth() - 80), Math.max(620, owner.getHeight() - 80));
        scene.getStylesheets().add(NotesApp.class.getResource("styles.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle(title + " — Devava Notes");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner instanceof Stage s) stage.getIcons().setAll(s.getIcons());
        stage.setOnCloseRequest(e -> {
            e.consume();
            cancel();
        });

        engine.setOnAlert(e -> {
            String message = e.getData();
            if (message.equals("ready")) {
                Platform.runLater(this::open);
            } else if (message.equals("opened")) {
                opened = true;
                view.requestFocus();
            } else if (message.startsWith("png:")) {
                String data = message.substring(4);
                Platform.runLater(() -> {
                    stage.close();
                    if (!data.isEmpty()) onDone.accept(Base64.getDecoder().decode(data));
                });
            } else if (message.startsWith("copy:")) {
                Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, message.substring(5)));
            } else if (message.equals("paste")) {
                Platform.runLater(this::pastePicture);
            } else if (message.startsWith("library:")) {
                try {
                    Vault.write(library, message.substring(8));
                } catch (IOException ex) {
                    errors.add("the library: " + ex.getMessage());
                }
            } else if (message.startsWith("error:")) {
                errors.add(message.substring(6));
                System.err.println("[draw.js] " + message.substring(6));
            }
        });
        engine.setPromptHandler(prompt -> {   // Pegar, in Excalidraw's menu: the text, or the pictures pasted here
            Clipboard clip = Clipboard.getSystemClipboard();
            if (clip.hasString()) return clip.getString();
            pastePicture();
            return null;
        });
        engine.setCreatePopupHandler(features -> {   // in the browser, once, not in a window of this page's
            WebEngine popup = new WebEngine();
            boolean[] sent = {false};
            popup.locationProperty().addListener((o, was, url) -> {
                if (sent[0] || url == null || url.isEmpty()) return;
                sent[0] = true;
                popup.getLoadWorker().cancel();
                // Excalidraw's link to its libraries carries this page's address, a path of this computer: left out
                onLink.accept(url.contains("?") && url.contains("referrer=file") ? url.substring(0, url.indexOf('?')) : url);
            });
            return popup;
        });
        engine.load(Editor.page("draw.html"));
    }

    private void open() {
        String shapes;
        try {
            shapes = Files.readString(library);
        } catch (IOException e) {
            shapes = "";   // none kept yet
        }
        call("setTheme", dark);
        call("openDrawing", png == null ? "" : Base64.getEncoder().encodeToString(png), shapes);
    }

    /** A picture from a file, into the drawing: to draw on a screenshot or a diagram, say. */
    private void choosePicture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Agregar una imagen al dibujo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) addPicture(file.toPath(), false);
    }

    /** Ctrl+V of a picture (a screenshot, or a picture's file), which the page cannot read itself: it goes where the mouse is. */
    private void pastePicture() {
        Clipboard clip = Clipboard.getSystemClipboard();
        if (clip.hasFiles()) {
            clip.getFiles().stream().map(File::toPath).filter(Vault::isImage).forEach(file -> addPicture(file, true));
        } else if (clip.hasImage()) {
            try {
                call("addImage", Base64.getEncoder().encodeToString(NotesApp.png(clip.getImage())), "image/png", true);
            } catch (IOException e) {
                errors.add("the pasted picture: " + e.getMessage());
            }
        }
    }

    private void addPicture(Path file, boolean pasted) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String type = name.endsWith(".png") ? "image/png" : name.endsWith(".gif") ? "image/gif" : name.endsWith(".svg") ? "image/svg+xml"
                : name.endsWith(".bmp") ? "image/bmp" : "image/jpeg";
        try {
            call("addImage", Base64.getEncoder().encodeToString(Files.readAllBytes(file)), type, pasted);
        } catch (IOException e) {
            errors.add(file + ": " + e.getMessage());
        }
    }

    /** Closes the window, after asking when that loses what was drawn. */
    private void cancel() {
        if (opened && Boolean.TRUE.equals(call("drawingChanged"))) {
            ButtonType discard = new ButtonType("Descartar", ButtonBar.ButtonData.OK_DONE);
            Alert ask = new Alert(Alert.AlertType.CONFIRMATION, "Lo que dibujaste no se guarda en la nota.", discard, ButtonType.CANCEL);
            ask.initOwner(stage);
            ask.setTitle("¿Descartar el dibujo?");
            ask.setHeaderText("¿Descartar el dibujo?");
            ask.getDialogPane().getStylesheets().setAll(stage.getScene().getStylesheets());
            ask.getDialogPane().lookupButton(discard).getStyleClass().add("danger");
            if (ask.showAndWait().orElse(ButtonType.CANCEL) != discard) return;
        }
        stage.close();
    }

    private Object call(String function, Object... args) {
        return ((JSObject) engine.executeScript("window")).call(function, args);
    }
}
