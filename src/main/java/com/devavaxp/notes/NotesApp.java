package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Badge;
import com.devavaxp.notes.NotebookType.Kind;
import com.devavaxp.notes.NotebookView.Entry;
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
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Devava Notes. Home shows the notebooks; a notebook shows the views of its type, the notes of the
 * chosen view, and the open note: its properties above, and below it the text, to write it or to
 * read it (Ctrl+E). A note saves itself a moment after it changes, and again whenever it is left.
 */
public final class NotesApp extends Application {

    static final String NAME = "Devava Notes";
    private static final KeyCombination NEW = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination READ = new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    Editor editor;
    NotebookView browser;
    private PropertiesPanel properties;
    private Stage stage;
    private Vault vault;
    private final BorderPane root = new BorderPane();
    private final HBox bar = new HBox(8);
    private final BorderPane notePane = new BorderPane();
    private final HBox noteHeader = new HBox(10);
    private final Label noteTitle = new Label(), kindName = new Label(), badge = new Label(), status = new Label();
    private final HBox tools = new HBox(4);
    private final Button mode = new Button("Read");
    private final PauseTransition autosave = new PauseTransition(Duration.millis(600));
    private SplitPane notebookView;

    /** The open notebook, its type and its notes; notebook is null on the home screen. */
    private Path notebook;
    private NotebookType type = NotebookType.GENERAL;
    private final List<Entry> entries = new ArrayList<>();
    /** The open note: its file and its Note (properties as edited, body as last saved); null when none is open. */
    private Path note;
    private Note current;
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
        browser = new NotebookView(this);
        properties = new PropertiesPanel(this::propertiesChanged, this::openLink, this::usedValues);
        autosave.setOnFinished(e -> save());

        bar.getStyleClass().add("bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        root.setTop(bar);

        noteTitle.getStyleClass().add("note-title");
        noteTitle.setMinWidth(Region.USE_PREF_SIZE);   // a narrow pane squeezes the buttons, not the title
        badge.setMinWidth(Region.USE_PREF_SIZE);
        mode.setMinWidth(Region.USE_PREF_SIZE);
        kindName.getStyleClass().add("kind");
        status.getStyleClass().add("status-error");
        mode.setTooltip(new Tooltip("Read or edit (Ctrl+E)"));
        mode.setOnAction(e -> toggleMode());
        noteHeader.getChildren().setAll(noteTitle, kindName, badge, grow(), status, tools, mode);
        noteHeader.getStyleClass().add("note-header");
        noteHeader.setAlignment(Pos.CENTER_LEFT);
        notebookView = new SplitPane(browser.sidebar, browser.notes, notePane);
        notebookView.setDividerPositions(0.16, 0.42);
        browser.tableMode().addListener((o, was, table) -> notebookView.setDividerPositions(0.16, table ? 0.68 : 0.42));
        SplitPane.setResizableWithParent(browser.sidebar, false);
        SplitPane.setResizableWithParent(browser.notes, false);

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(NotesApp.class.getResource("styles.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (NEW.match(e)) {
                if (notebook == null) newNotebook();
                else newNote(type.kinds().get(0));
                e.consume();
            } else if (READ.match(e) && note != null) {
                toggleMode();
                e.consume();
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(560);
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
        VBox home = new VBox(14);
        List<Upcoming> upcoming = upcoming(notebooks);
        if (!upcoming.isEmpty()) {
            VBox rows = new VBox(2);
            upcoming.stream().limit(8).forEach(u -> rows.getChildren().add(upcomingRow(u)));
            rows.getStyleClass().add("upcoming");
            home.getChildren().addAll(sectionTitle("UPCOMING"), rows);
        }
        home.getChildren().addAll(sectionTitle("NOTEBOOKS"), cards);
        home.getStyleClass().add("home");
        ScrollPane scroll = new ScrollPane(home);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("home-scroll");
        return scroll;
    }

    /** A pending assignment or a coming exam, in one of the Class Notes notebooks. */
    private record Upcoming(Vault.Notebook notebook, Path path, Note note) {
    }

    /** What is still to do in every course: late assignments first, then by day. */
    private static List<Upcoming> upcoming(List<Vault.Notebook> notebooks) {
        LocalDate today = LocalDate.now();
        List<Upcoming> items = new ArrayList<>();
        for (Vault.Notebook nb : notebooks) {
            if (!nb.type().equals(NotebookType.CLASS_NOTES)) continue;
            try {
                for (Path p : Vault.notes(nb.dir())) {
                    Note n = Note.parse(Vault.read(p));
                    if (Agenda.upcoming(n, today)) items.add(new Upcoming(nb, p, n));
                }
            } catch (IOException ignored) {
                // A course that cannot be read right now just has nothing to show.
            }
        }
        items.sort(Comparator.comparing((Upcoming u) -> Agenda.when(u.note())).thenComparing(u -> Vault.title(u.path()), Vault::compareNatural));
        return items;
    }

    private Button upcomingRow(Upcoming u) {
        Badge b = Agenda.badge(u.note(), LocalDate.now());
        Label when = new Label(b == null ? "" : b.text()), title = new Label(Vault.title(u.path())), course = new Label(u.notebook().name());
        when.setStyle(b == null ? "" : "-fx-text-fill: " + b.color() + "; -fx-font-weight: bold;");
        when.setMinWidth(120);
        course.getStyleClass().add("card-meta");
        HBox line = new HBox(12, when, title, course);
        line.setAlignment(Pos.CENTER_LEFT);
        Button row = new Button(null, line);
        row.getStyleClass().add("upcoming-row");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setOnAction(e -> {
            openNotebook(u.notebook().dir());
            openNote(u.path());
            browser.reveal(u.path());
        });
        return row;
    }

    private static Label sectionTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("section-title");
        return title;
    }

    private Button card(Vault.Notebook nb) {
        Label name = new Label(nb.name());
        name.getStyleClass().add("card-title");
        Label kind = new Label(nb.type().name());
        kind.getStyleClass().add("card-type");
        Label meta = new Label(count(nb.notes()) + " · edited " + DAY.format(nb.edited().toInstant()));
        meta.getStyleClass().add("card-meta");
        Button card = new Button(null, new VBox(4, name, kind, meta));
        card.getStyleClass().add("card");
        card.setOnAction(e -> openNotebook(nb.dir()));
        card.setContextMenu(new ContextMenu(notebookItems(nb.dir())));
        return card;
    }

    private MenuItem[] notebookItems(Path dir) {
        List<MenuItem> items = new ArrayList<>(List.of(
                item("Rename…", () -> renameNotebook(dir)),
                item("Change type…", () -> changeType(dir))));
        if (Vault.type(dir).equals(NotebookType.COMPETITIVE_PROGRAMMING)) items.add(item("C++ template…", () -> editTemplate(dir)));
        items.addAll(List.of(
                item("Show in folder", () -> showInFolder(dir)),
                new SeparatorMenuItem(),
                danger(item("Move to trash…", () -> trashNotebook(dir)))));
        return items.toArray(MenuItem[]::new);
    }

    /** The C++ that new problems and "+ Solution" start with, kept in the notebook's .notebook.json. */
    private void editTemplate(Path dir) {
        TextArea code = new TextArea(template(dir));
        code.getStyleClass().add("code-area");
        code.setPrefColumnCount(64);
        code.setPrefRowCount(18);
        Label about = new Label("New problems and + Solution start with it (LeetCode problems start empty).");
        about.getStyleClass().add("type-description");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("C++ template");
        dialog.setHeaderText("C++ template");
        style(dialog);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(8, about, code));
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        try {
            Vault.setMeta(dir, "cpp", code.getText());
        } catch (IOException e) {
            error("Couldn't save the template", reason(e));
        }
    }

    private static String template(Path dir) {
        String own = Vault.meta(dir, "cpp");
        return own.isBlank() ? Judges.CPP : own.stripTrailing();
    }

    void newNotebook() {
        askNotebook("New notebook", NotebookType.GENERAL, true).ifPresent(choice -> {
            try {
                openNotebook(createNotebook(choice.name(), choice.type()));
            } catch (IOException e) {
                error("Couldn't create the notebook", reason(e));
            }
        });
    }

    Path createNotebook(String name, NotebookType type) throws IOException {
        return vault.createNotebook(name, type);
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
                if (openNote != null) openNote(renamed.resolve(openNote.getFileName()));
            } catch (IOException e) {
                error("Couldn't rename the notebook", reason(e));
            }
        });
    }

    private void changeType(Path dir) {
        askNotebook("Change “" + dir.getFileName() + "” to", Vault.type(dir), false).ifPresent(choice -> {
            boolean open = dir.equals(notebook);
            if (open && !leaveNote()) return;
            try {
                Vault.setType(dir, choice.type());
                if (open) openNotebook(dir);
                else showHome();
            } catch (IOException e) {
                error("Couldn't change the type", reason(e));
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
    // A notebook: its views, its notes and the open note
    // ------------------------------------------------------------------

    void openNotebook(Path dir) {
        if (!leaveNote()) return;
        notebook = dir;
        type = Vault.type(dir);
        closeNote();
        Label name = new Label(dir.getFileName().toString());
        name.getStyleClass().add("crumb");
        Label typeName = new Label(type.name());
        typeName.getStyleClass().add("kind");
        bar.getChildren().setAll(button("‹ Home", "flat", e -> showHome()), name, typeName, grow(), newButton(),
                more(notebookItems(dir)));
        loadEntries(true);
        browser.open(type, entries);
        root.setCenter(notebookView);
        stage.setTitle(dir.getFileName() + " — " + NAME);
    }

    /** "+ New note" for one kind of note; with several, a menu of them (Ctrl+N makes the first). */
    private Node newButton() {
        Kind first = type.kinds().get(0);
        if (type.kinds().size() == 1) {
            Button add = button("+ New " + first.name().toLowerCase(Locale.ROOT), "primary", e -> newNote(first));
            add.setTooltip(new Tooltip("Ctrl+N"));
            return add;
        }
        MenuButton add = new MenuButton("+ New  ▾");
        type.kinds().forEach(k -> add.getItems().add(item(k.name(), () -> newNote(k))));
        add.getStyleClass().add("primary");
        add.setTooltip(new Tooltip("Ctrl+N: " + first.name().toLowerCase(Locale.ROOT)));
        return add;
    }

    /** Reads the properties of every note in the notebook; the open note keeps its own (maybe unsaved) ones. */
    private void loadEntries(boolean showErrors) {
        List<Path> paths;
        try {
            paths = Vault.notes(notebook);
        } catch (IOException e) {
            if (showErrors) error("Couldn't list the notes", reason(e));
            return;
        }
        List<Entry> loaded = new ArrayList<>();
        for (Path p : paths) {
            if (p.equals(note)) {
                loaded.add(new Entry(p, current, seen));
                continue;
            }
            try {
                loaded.add(new Entry(p, Note.parse(Vault.read(p)), Files.getLastModifiedTime(p)));
            } catch (IOException e) {
                loaded.add(new Entry(p, Note.parse(""), FileTime.fromMillis(0)));   // listed anyway; opening it says why
            }
        }
        entries.clear();
        entries.addAll(loaded);
    }

    void openNote(Path p) {
        if (!leaveNote()) {
            browser.select(note);
            return;
        }
        try {
            String text = Vault.read(p);
            display(p, Note.parse(text), Files.getLastModifiedTime(p));
        } catch (IOException e) {
            error("Couldn't open “" + Vault.title(p) + "”", reason(e));
            browser.select(note);
        }
    }

    /** Shows a note as just read: its properties above, its body in the editor. */
    private void display(Path p, Note n, FileTime modified) {
        note = p;
        current = n;
        seen = modified;
        dirty = false;
        entries.replaceAll(e -> e.path().equals(p) ? new Entry(p, n, modified) : e);
        Kind kind = type.kindOf(n);
        noteTitle.setText(Vault.title(p));
        kindName.setText(type.kinds().size() > 1 ? kind.name() : "");
        status.setText("");
        showBadge();
        tools.getChildren().clear();
        if (type.equals(NotebookType.COMPETITIVE_PROGRAMMING)) {
            if (kind.id().equals("problem")) tools.getChildren().add(button("+ Solution", "flat", e -> newSolution()));
            tools.getChildren().add(button("Insert snippet…", "flat", e -> insertSnippet()));
        }
        properties.show(kind, n);
        editor.open(n.body);
        notePane.setTop(new VBox(noteHeader, properties.node));
        notePane.setCenter(editor.view);
        browser.select(p);
    }

    /** A problem's difficulty in its judge's color, and its level when that says more ("800 · Easy"). */
    private void showBadge() {
        Badge b = current == null ? null : type.badge(current);
        String level = current == null ? "" : Judges.level(current);
        badge.setText(b == null ? "" : b.text().equalsIgnoreCase(level) || level.isEmpty() ? b.text() : b.text() + " · " + level);
        badge.setStyle(b == null || b.color().isEmpty() ? "" : "-fx-text-fill: " + b.color() + "; -fx-font-weight: bold;");
    }

    private void closeNote() {
        autosave.stop();
        note = null;
        current = null;
        dirty = false;
        notePane.setTop(null);
        notePane.setCenter(placeholder("No note open", "Pick a note, or press Ctrl+N to write a new one."));
    }

    void newNote(Kind kind) {
        if (notebook == null) return;
        boolean hasLink = kind.fields().stream().anyMatch(f -> f.key().equals("url"));
        Optional<String[]> answer = hasLink ? askProblem()
                : ask("New " + kind.name().toLowerCase(Locale.ROOT), "Title", "").map(title -> new String[]{title, ""});
        answer.ifPresent(a -> {
            try {
                createNote(kind, a[0], a[1]);
            } catch (IOException e) {
                error("Couldn't create the note", reason(e));
            }
        });
    }

    /**
     * Creates a note of that kind from its template and opens it, ready to write. A problem's link
     * fills in its judge and id, and its solution starts with the notebook's C++ template.
     */
    Path createNote(Kind kind, String title, String link) throws IOException {
        if (!leaveNote()) return null;
        Note start = Note.parse(kind.template().replace("{today}", LocalDate.now().toString()));
        if (!link.isBlank()) {
            start.set("url", link);
            Judges.fromLink(link).ifPresent(problem -> {
                start.set("judge", problem.judge());
                start.set("id", problem.id());
            });
        }
        if (kind.id().equals("problem") && !start.get("judge").equals("LeetCode")) {
            start.body = start.body.replace("```cpp\n\n```", "```cpp\n" + template(notebook) + "\n```");
        }
        Path p = Vault.createNote(notebook, title, start.text());
        FileTime modified = Files.getLastModifiedTime(p);
        Note n = Note.parse(Vault.read(p));
        entries.add(new Entry(p, n, modified));
        setReading(false);
        display(p, n, modified);
        browser.update();
        browser.reveal(p);
        editor.focus();
        return p;
    }

    void renameNote(Path p) {
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
                loadEntries(true);
                browser.update();
            } catch (IOException e) {
                error("Couldn't rename the note", reason(e));
            }
        });
    }

    void trashNote(Path p) {
        if (!confirm("Move “" + Vault.title(p) + "” to the trash?", "You can restore it from the trash.", "Move to trash")) return;
        boolean open = p.equals(note);
        if (open) save();   // its latest text goes to the trash with it
        try {
            Vault.trash(p);
            if (open) closeNote();
            loadEntries(true);
            browser.update();
        } catch (IOException e) {
            error("Couldn't move “" + Vault.title(p) + "” to the trash", reason(e));
        }
    }

    /** Another solution at the cursor, starting with the C++ template: "Brute force", "Optimal"… */
    private void newSolution() {
        setReading(false);
        String code = current.get("judge").equals("LeetCode") ? "" : template(notebook);
        editor.insert("\n### Solution · O( )\n\n```cpp\n" + code + "\n```\n");
        editor.focus();
    }

    /** Picks a snippet of the Code Library and puts its code at the cursor. */
    private void insertSnippet() {
        List<Entry> snippets = entries.stream()
                .filter(e -> type.kindOf(e.note()).id().equals("snippet") && !e.path().equals(note)).toList();
        if (snippets.isEmpty()) {
            error("The Code Library is empty", "Add a snippet with + New ▾ → Snippet, with its code in a ```cpp block.");
            return;
        }
        TextField filter = new TextField();
        filter.setPromptText("Filter");
        ListView<Entry> list = new ListView<>();
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Entry e, boolean empty) {
                super.updateItem(e, empty);
                String topics = e == null ? "" : String.join(", ", e.note().list("algorithms"));
                setText(empty || e == null ? null : topics.isEmpty() ? e.title() : e.title() + "  ·  " + topics);
            }
        });
        list.getItems().setAll(snippets);
        list.getSelectionModel().selectFirst();
        filter.textProperty().addListener((o, was, now) -> {
            String wanted = NotebookView.fold(now.strip());
            list.getItems().setAll(snippets.stream().filter(e -> NotebookView.fold(e.title() + " "
                    + String.join(" ", e.note().list("algorithms")) + " " + String.join(" ", e.note().list("techniques"))).contains(wanted)).toList());
            list.getSelectionModel().selectFirst();
        });
        list.setPrefSize(420, 260);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Insert snippet");
        dialog.setHeaderText("Insert snippet");
        style(dialog);
        ButtonType insert = new ButtonType("Insert", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(insert, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(8, filter, list));
        dialog.getDialogPane().lookupButton(insert).disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                ((Button) dialog.getDialogPane().lookupButton(insert)).fire();
            }
        });
        Platform.runLater(filter::requestFocus);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != insert) return;
        insertSnippet(list.getSelectionModel().getSelectedItem().path());
    }

    /** Puts the first code block of a snippet at the cursor. */
    void insertSnippet(Path snippet) {
        Entry entry = entries.stream().filter(e -> e.path().equals(snippet)).findFirst().orElse(null);
        Optional<Note.Code> code = entry == null ? Optional.empty() : entry.note().firstCode();
        if (code.isEmpty()) {
            error("Nothing to insert", "“" + Vault.title(snippet) + "” has no block of code yet.");
            return;
        }
        setReading(false);
        editor.insertCode(code.get().text(), code.get().language());
    }

    /** The values a list property already has in this notebook, for its ▾ menu. */
    private List<String> usedValues(String key) {
        return entries.stream().flatMap(e -> e.note().list(key).stream()).distinct().toList();
    }

    ContextMenu noteMenu(Path p) {
        ContextMenu menu = new ContextMenu(item("Rename…", () -> renameNote(p)));
        Note n = entries.stream().filter(e -> e.path().equals(p)).map(Entry::note).findFirst().orElse(null);
        if (n != null && type.equals(NotebookType.CLASS_NOTES) && type.kindOf(n).id().equals("assignment")) {
            boolean done = n.get("status").equalsIgnoreCase("Done");
            menu.getItems().add(item(done ? "Mark as pending" : "Mark as done", () -> setProperty(p, "status", done ? "Pending" : "Done")));
        }
        menu.getItems().addAll(new SeparatorMenuItem(), danger(item("Move to trash…", () -> trashNote(p))));
        return menu;
    }

    /** Sets one property of a note from the list, whether it is the open note or not. */
    void setProperty(Path p, String key, String value) {
        if (p.equals(note)) {
            current.set(key, value);
            properties.show(type.kindOf(current), current);
            propertiesChanged(key);
            return;
        }
        try {
            Note n = Note.parse(Vault.read(p));
            n.set(key, value);
            Vault.write(p, n.text());
        } catch (IOException e) {
            error("Couldn't change “" + Vault.title(p) + "”", reason(e));
            return;
        }
        loadEntries(true);
        browser.update();
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

    /**
     * A property changed: save soon, and the note may now belong in other groups. A problem's new
     * link fills in its judge and id.
     */
    private void propertiesChanged(String key) {
        if (key.equals("url")) {
            Judges.fromLink(current.get("url")).ifPresent(problem -> {
                current.set("judge", problem.judge());
                current.set("id", problem.id());
                properties.show(type.kindOf(current), current);   // shows what it filled in
            });
        }
        edited();
        showBadge();
        browser.update();
    }

    /** Writes the open note if it changed. False when that failed: the text stays in the editor. */
    boolean save() {
        autosave.stop();
        if (note == null || !dirty) return true;
        try {
            current.body = editor.text();
            Vault.write(note, current.text());
            seen = Files.getLastModifiedTime(note);
            entries.replaceAll(e -> e.path().equals(note) ? new Entry(note, current, seen) : e);
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
    void refreshFromDisk() {
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
                FileTime modified = Files.getLastModifiedTime(note);
                if (text.replace("\r\n", "\n").equals(current.text())) seen = modified;
                else display(note, Note.parse(text), modified);
            }
        } catch (NoSuchFileException e) {
            closeNote();
        } catch (IOException ignored) {
            // Unreadable for a moment (a sync in progress): keep what is shown.
        }
        loadEntries(false);
        browser.update();
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

    /** Asks for a new problem's link and title: a known link suggests the title ("CF 4A", "Two Sum"). */
    private Optional<String[]> askProblem() {
        TextField link = new TextField(), title = new TextField();
        link.setPromptText("https://codeforces.com/…, atcoder.jp/…, leetcode.com/… (optional)");
        link.setPrefColumnCount(36);
        title.setPromptText("Title");
        String[] suggested = {""};
        link.textProperty().addListener((o, was, now) -> {
            if (!title.getText().isBlank() && !title.getText().equals(suggested[0])) return;   // typed by hand: keep it
            suggested[0] = Judges.fromLink(now).map(Judges.Problem::title).orElse("");
            title.setText(suggested[0]);
        });
        Label linkName = new Label("Link"), titleName = new Label("Title");
        linkName.getStyleClass().add("property-name");
        titleName.getStyleClass().add("property-name");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New problem");
        dialog.setHeaderText("New problem");
        style(dialog);
        ButtonType create = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(create, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(6, linkName, link, titleName, title));
        dialog.getDialogPane().lookupButton(create).disableProperty().bind(title.textProperty().map(String::isBlank));
        Platform.runLater(link::requestFocus);
        return dialog.showAndWait().filter(b -> b == create).map(b -> new String[]{title.getText().strip(), link.getText().strip()});
    }

    private record NotebookChoice(String name, NotebookType type) {
    }

    /** Asks for a notebook's type, and for its name when {@code withName}. */
    private Optional<NotebookChoice> askNotebook(String title, NotebookType selected, boolean withName) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        style(dialog);
        TextField name = new TextField();
        name.setPromptText("Name");
        name.setPrefColumnCount(28);
        VBox content = new VBox(12);
        if (withName) content.getChildren().add(name);
        ToggleGroup types = new ToggleGroup();
        for (NotebookType t : NotebookType.ALL) {
            RadioButton option = new RadioButton(t.name());
            option.setUserData(t);
            option.setToggleGroup(types);
            option.setSelected(t.equals(selected));
            Label about = new Label(t.description());
            about.getStyleClass().add("type-description");
            content.getChildren().add(new VBox(2, option, about));
        }
        ButtonType ok = new ButtonType(withName ? "Create" : "Change", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        if (withName) {
            dialog.getDialogPane().lookupButton(ok).disableProperty().bind(name.textProperty().map(String::isBlank));
            Platform.runLater(name::requestFocus);
        }
        return dialog.showAndWait().filter(b -> b == ok)
                .map(b -> new NotebookChoice(name.getText().strip(), (NotebookType) types.getSelectedToggle().getUserData()));
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
