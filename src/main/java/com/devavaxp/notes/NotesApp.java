package com.devavaxp.notes;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Devava Notes. Home shows the notebooks; a notebook shows its notes on the left and the open
 * note on the right, to write it or to read it (Ctrl+E). A note saves itself a moment after
 * typing stops, and again whenever it is left.
 */
public final class NotesApp extends Application {

    static final String NAME = "Devava Notes";
    private static final KeyCombination NEW = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination READ = new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    Editor editor;
    private Stage stage;
    private Vault vault;
    private final BorderPane root = new BorderPane();
    private final HBox bar = new HBox(8);
    private final ListView<Path> list = new ListView<>();
    private final BorderPane notePane = new BorderPane();
    private final HBox noteHeader = new HBox(12);
    private final Label noteTitle = new Label(), status = new Label();
    private final Button mode = new Button("Read");
    private final PauseTransition autosave = new PauseTransition(Duration.millis(600));
    private SplitPane notebookView;

    /** The open notebook folder and note file; null on the home screen, or with no note open. */
    private Path notebook, note;
    private boolean dirty, reading;
    /** The note's modification time as this app last read or wrote it, to notice edits made elsewhere. */
    private FileTime seen;
    private String saveError = "";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        show(stage, new Vault(Vault.defaultRoot()));
    }

    void show(Stage stage, Vault vault) {
        this.stage = stage;
        this.vault = vault;
        editor = new Editor(this::edited, this::openLink);
        autosave.setOnFinished(e -> save());

        bar.getStyleClass().add("bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        root.setTop(bar);

        list.getStyleClass().add("notes");
        list.setPlaceholder(new Label("No notes yet"));
        list.setMinWidth(180);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Path p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : Vault.title(p));
                setContextMenu(empty || p == null ? null : new ContextMenu(
                        item("Rename…", () -> renameNote(p)),
                        new SeparatorMenuItem(),
                        danger(item("Move to trash…", () -> trashNote(p)))));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, old, p) -> {
            if (p != null && !p.equals(note)) openNote(p);
        });
        list.setOnKeyPressed(e -> {
            Path p = list.getSelectionModel().getSelectedItem();
            if (p == null) return;
            if (e.getCode() == KeyCode.ENTER) editor.focus();
            else if (e.getCode() == KeyCode.F2) renameNote(p);
            else if (e.getCode() == KeyCode.DELETE) trashNote(p);
        });

        noteTitle.getStyleClass().add("note-title");
        status.getStyleClass().add("status-error");
        mode.setTooltip(new Tooltip("Read or edit (Ctrl+E)"));
        mode.setOnAction(e -> toggleMode());
        noteHeader.getChildren().setAll(noteTitle, grow(), status, mode);
        noteHeader.getStyleClass().add("note-header");
        noteHeader.setAlignment(Pos.CENTER_LEFT);
        notebookView = new SplitPane(list, notePane);
        notebookView.setDividerPositions(0.24);
        SplitPane.setResizableWithParent(list, false);

        Scene scene = new Scene(root, 1200, 780);
        scene.getStylesheets().add(NotesApp.class.getResource("styles.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (NEW.match(e)) {
                if (notebook == null) newNotebook();
                else newNote();
                e.consume();
            } else if (READ.match(e) && note != null) {
                toggleMode();
                e.consume();
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(820);
        stage.setMinHeight(540);
        stage.setOnCloseRequest(e -> {
            if (!leaveNote()) e.consume();
        });
        stage.focusedProperty().addListener((o, was, focused) -> {
            if (focused) refreshFromDisk();
        });
        showHome();
        stage.show();
    }

    // ------------------------------------------------------------------
    // Home: the notebooks
    // ------------------------------------------------------------------

    void showHome() {
        if (!leaveNote()) return;
        notebook = null;
        note = null;
        Label name = new Label(NAME);
        name.getStyleClass().add("app-name");
        Button add = button("+ New notebook", "primary", e -> newNotebook());
        add.setTooltip(new Tooltip("Ctrl+N"));
        bar.getChildren().setAll(name, grow(), add, more(item("Open data folder", () -> showInFolder(vault.root))));
        root.setCenter(homeContent(true));
        stage.setTitle(NAME);
    }

    private Node homeContent(boolean showErrors) {
        List<Vault.Notebook> notebooks;
        try {
            notebooks = vault.notebooks();
        } catch (IOException e) {
            notebooks = List.of();
            if (showErrors) error("Couldn't open your notes", vault.root + "\n\n" + reason(e));
        }
        if (notebooks.isEmpty()) {
            VBox empty = placeholder("No notebooks yet", "A notebook keeps the notes of one subject, course or project.");
            empty.getChildren().add(button("+ New notebook", "primary", e -> newNotebook()));
            return empty;
        }
        FlowPane cards = new FlowPane(16, 16);
        cards.getChildren().setAll(notebooks.stream().map(this::card).toList());
        Label title = new Label("NOTEBOOKS");
        title.getStyleClass().add("section-title");
        VBox home = new VBox(14, title, cards);
        home.getStyleClass().add("home");
        ScrollPane scroll = new ScrollPane(home);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("home-scroll");
        return scroll;
    }

    private Button card(Vault.Notebook nb) {
        Label name = new Label(nb.name());
        name.getStyleClass().add("card-title");
        Label meta = new Label(count(nb.notes()) + " · edited " + DAY.format(nb.edited().toInstant()));
        meta.getStyleClass().add("card-meta");
        Button card = new Button(null, new VBox(6, name, meta));
        card.getStyleClass().add("card");
        card.setOnAction(e -> openNotebook(nb.dir()));
        card.setContextMenu(new ContextMenu(notebookItems(nb.dir())));
        return card;
    }

    private MenuItem[] notebookItems(Path dir) {
        return new MenuItem[]{
                item("Rename…", () -> renameNotebook(dir)),
                item("Show in folder", () -> showInFolder(dir)),
                new SeparatorMenuItem(),
                danger(item("Move to trash…", () -> trashNotebook(dir)))};
    }

    void newNotebook() {
        ask("New notebook", "Name", "").ifPresent(name -> {
            try {
                openNotebook(createNotebook(name));
            } catch (IOException e) {
                error("Couldn't create the notebook", reason(e));
            }
        });
    }

    Path createNotebook(String name) throws IOException {
        return vault.createNotebook(name);
    }

    private void renameNotebook(Path dir) {
        ask("Rename notebook", "Name", dir.getFileName().toString()).ifPresent(name -> {
            boolean open = dir.equals(notebook);
            if (open && !leaveNote()) return;
            Path openNote = note;
            try {
                Path renamed = Vault.rename(dir, name);
                if (!open) {
                    showHome();
                    return;
                }
                openNotebook(renamed);
                if (openNote != null) list.getSelectionModel().select(renamed.resolve(openNote.getFileName()));
            } catch (IOException e) {
                error("Couldn't rename the notebook", reason(e));
            }
        });
    }

    private void trashNotebook(Path dir) {
        int notes;
        try {
            notes = Vault.notes(dir).size();
        } catch (IOException e) {
            notes = 0;
        }
        String name = dir.getFileName().toString();
        if (!confirm("Move “" + name + "” to the trash?",
                "The notebook and its " + count(notes) + " go to the trash, where you can restore them.", "Move to trash")) return;
        if (dir.equals(notebook) && !leaveNote()) return;
        try {
            Vault.trash(dir);
            note = null;
            showHome();
        } catch (IOException e) {
            error("Couldn't move “" + name + "” to the trash", reason(e));
        }
    }

    // ------------------------------------------------------------------
    // A notebook: its notes and the open note
    // ------------------------------------------------------------------

    void openNotebook(Path dir) {
        if (!leaveNote()) return;
        notebook = dir;
        closeNote();
        Label name = new Label(dir.getFileName().toString());
        name.getStyleClass().add("crumb");
        Button add = button("+ New note", "primary", e -> newNote());
        add.setTooltip(new Tooltip("Ctrl+N"));
        bar.getChildren().setAll(button("‹ Home", "flat", e -> showHome()), name, grow(), add, more(notebookItems(dir)));
        refreshNotes(null);
        root.setCenter(notebookView);
        list.requestFocus();
        stage.setTitle(dir.getFileName() + " — " + NAME);
    }

    private void refreshNotes(Path select) {
        try {
            listNotes(select);
        } catch (IOException e) {
            list.getItems().clear();
            error("Couldn't list the notes", reason(e));
        }
    }

    /** Lists the notes again, selecting {@code select} or else the open note. */
    private void listNotes(Path select) throws IOException {
        list.getItems().setAll(Vault.notes(notebook));
        Path p = select != null ? select : note;
        if (p != null) list.getSelectionModel().select(p);
    }

    void openNote(Path p) {
        if (!leaveNote()) {
            Platform.runLater(() -> list.getSelectionModel().select(note));
            return;
        }
        try {
            String text = Vault.read(p);
            seen = Files.getLastModifiedTime(p);
            note = p;
            dirty = false;
            noteTitle.setText(Vault.title(p));
            status.setText("");
            editor.open(text);
            notePane.setTop(noteHeader);
            notePane.setCenter(editor.view);
        } catch (IOException e) {
            error("Couldn't open “" + Vault.title(p) + "”", reason(e));
            Platform.runLater(() -> list.getSelectionModel().select(note));
        }
    }

    private void closeNote() {
        autosave.stop();
        note = null;
        dirty = false;
        notePane.setTop(null);
        notePane.setCenter(placeholder("No note open", "Pick a note on the left, or press Ctrl+N to write a new one."));
    }

    void newNote() {
        if (notebook == null) return;
        ask("New note", "Title", "").ifPresent(title -> {
            try {
                createNote(title);
            } catch (IOException e) {
                error("Couldn't create the note", reason(e));
            }
        });
    }

    /** Creates a note in the open notebook and opens it, ready to write. */
    Path createNote(String title) throws IOException {
        if (!leaveNote()) return null;
        Path p = Vault.createNote(notebook, title);
        setReading(false);
        refreshNotes(p);
        editor.focus();
        return p;
    }

    private void renameNote(Path p) {
        ask("Rename note", "Title", Vault.title(p)).ifPresent(title -> {
            boolean open = p.equals(note);
            if (open && !save()) {
                error("Couldn't rename the note", "Its latest changes aren't saved: " + saveError);
                return;
            }
            try {
                Path renamed = Vault.rename(p, title);
                if (open) {
                    note = renamed;
                    noteTitle.setText(Vault.title(renamed));
                }
                refreshNotes(renamed);
            } catch (IOException e) {
                error("Couldn't rename the note", reason(e));
            }
        });
    }

    private void trashNote(Path p) {
        if (!confirm("Move “" + Vault.title(p) + "” to the trash?", "You can restore it from the trash.", "Move to trash")) return;
        boolean open = p.equals(note);
        if (open) save();   // its latest text goes to the trash with it
        try {
            Vault.trash(p);
            if (open) closeNote();
            refreshNotes(null);
        } catch (IOException e) {
            error("Couldn't move “" + Vault.title(p) + "” to the trash", reason(e));
        }
    }

    void toggleMode() {
        if (note != null) setReading(!reading);
    }

    private void setReading(boolean on) {
        reading = on;
        mode.setText(on ? "Edit" : "Read");
        editor.setReading(on);
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    private void edited() {
        dirty = true;
        autosave.playFromStart();
    }

    /** Writes the open note if it changed. False when that failed: the text stays in the editor. */
    boolean save() {
        autosave.stop();
        if (note == null || !dirty) return true;
        try {
            Vault.write(note, editor.text());
            seen = Files.getLastModifiedTime(note);
            dirty = false;
            status.setText("");
            return true;
        } catch (IOException e) {
            saveError = reason(e);
            status.setText("Not saved: " + saveError);   // tried again on the next change or when leaving
            return false;
        }
    }

    /** Saves the open note before leaving it; if that fails, asks whether to leave it anyway. */
    private boolean leaveNote() {
        return save() || confirm("Leave without saving?", "The latest changes to “" + Vault.title(note)
                + "” couldn't be saved (" + saveError + "). If you leave, they are lost.", "Leave anyway");
    }

    /**
     * Picks up what other programs (or OneDrive) changed while the window was in the background.
     * Quietly: an error dialog here would come back every time the window is focused again.
     */
    private void refreshFromDisk() {
        if (notebook == null) {
            root.setCenter(homeContent(false));
            return;
        }
        if (!Files.isDirectory(notebook)) {
            closeNote();
            showHome();
            return;
        }
        try {
            if (note != null && !dirty && !Files.getLastModifiedTime(note).equals(seen)) {
                String text = Vault.read(note);
                seen = Files.getLastModifiedTime(note);
                if (!text.equals(editor.text())) editor.open(text);
            }
        } catch (NoSuchFileException e) {
            closeNote();
        } catch (IOException ignored) {
            // Unreadable for a moment (a sync in progress): keep what is shown.
        }
        try {
            listNotes(null);
        } catch (IOException ignored) {
            // Same: the list stays as it was.
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void openLink(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.startsWith("https://") || u.startsWith("http://") || u.startsWith("mailto:")) getHostServices().showDocument(url);
    }

    private void showInFolder(Path dir) {
        try {
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException | RuntimeException e) {
            error("Couldn't open the folder", dir.toString());
        }
    }

    /** A short reason people understand; the exception's own message is often just a path. */
    private static String reason(IOException e) {
        if (e instanceof FileAlreadyExistsException) return "there's already one with that name";
        if (e instanceof AccessDeniedException) return "access to the file was denied";
        if (e instanceof NoSuchFileException) return "the file is no longer there";
        if (e instanceof CharacterCodingException) return "it isn't UTF-8 text";
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private static String count(int notes) {
        return notes == 0 ? "no notes" : notes == 1 ? "1 note" : notes + " notes";
    }

    private static Button button(String text, String style, EventHandler<ActionEvent> action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setOnAction(action);
        return b;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private static MenuItem danger(MenuItem item) {
        item.getStyleClass().add("danger");
        return item;
    }

    private static MenuButton more(MenuItem... items) {
        MenuButton more = new MenuButton("⋯", null, items);
        more.getStyleClass().addAll("flat", "more-menu");
        return more;
    }

    private static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static VBox placeholder(String title, String text) {
        Label t = new Label(title), s = new Label(text);
        t.getStyleClass().add("empty-title");
        s.getStyleClass().add("empty-text");
        VBox box = new VBox(8, t, s);
        box.getStyleClass().add("placeholder");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void style(Dialog<?> dialog) {
        dialog.initOwner(stage);
        dialog.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
        dialog.getDialogPane().getStyleClass().add("dialog");
        dialog.setGraphic(null);
    }

    private Optional<String> ask(String title, String label, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(label);
        style(dialog);
        dialog.getEditor().setPrefColumnCount(28);
        return dialog.showAndWait().map(String::strip).filter(s -> !s.isEmpty());
    }

    /** A yes/no question whose "yes" loses or moves something, so it shows in red. */
    private boolean confirm(String title, String message, String action) {
        ButtonType yes = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, yes, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(title);
        style(alert);
        alert.getDialogPane().lookupButton(yes).getStyleClass().add("danger");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == yes;
    }

    private void error(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle(title);
        alert.setHeaderText(title);
        style(alert);
        alert.showAndWait();
    }
}
