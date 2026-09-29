package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Badge;
import com.devavaxp.notes.NotebookType.Kind;
import com.devavaxp.notes.NotebookView.Entry;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Devava Notes. Home shows the notebooks; a notebook shows the views of its type, the notes of the
 * chosen view, and the open note: its properties above, and below it the text, to write it or to
 * read it (Ctrl+E). A note saves itself a moment after it changes, and again whenever it is left.
 */
public final class NotesApp extends Application {

    static final String NAME = "Devava Notes";
    private static final KeyCombination NEW = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination READ = new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination QUICK_OPEN = new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SEARCH = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination PASTE = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
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
    /** The note's buttons: at the end of its header, or on a row of their own when the pane is narrow. */
    private final HBox actions = new HBox(10), actionsRow = new HBox();
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
    /** The dark theme, remembered in settings.json; the icon, of the window and of Home. */
    private boolean dark;
    private Image icon;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        show(stage, new Vault(Vault.defaultRoot()));
    }

    void show(Stage stage, Vault vault) {
        Locale.setDefault(Locale.ENGLISH);   // the app speaks English, the stock dialogs' "Cancel" and the calendars too
        this.stage = stage;
        this.vault = vault;
        editor = new Editor(this::edited, this::openLink, this::openTitle, tag -> browser.showTag(tag));
        browser = new NotebookView(this);
        properties = new PropertiesPanel(this::propertiesChanged, this::openLink, this::usedValues);
        autosave.setOnFinished(e -> save());
        // Images dropped on the note go to attachments/ (the WebView would otherwise open the file).
        editor.view.addEventFilter(DragEvent.DRAG_OVER, e -> {
            if (note == null || reading || !e.getDragboard().hasFiles()) return;
            e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        editor.view.addEventFilter(DragEvent.DRAG_DROPPED, e -> {
            if (note == null || reading || !e.getDragboard().hasFiles()) return;
            try {
                attachImages(e.getDragboard().getFiles());
            } catch (IOException ex) {
                error("Couldn't add the image", reason(ex));
            }
            e.setDropCompleted(true);
            e.consume();
        });

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
        actions.getChildren().setAll(status, tools, mode);
        actions.setAlignment(Pos.CENTER_RIGHT);
        noteHeader.getChildren().setAll(noteTitle, kindName, badge, grow(), actions);
        noteHeader.getStyleClass().add("note-header");
        noteHeader.setAlignment(Pos.CENTER_LEFT);
        noteHeader.widthProperty().addListener((o, was, width) -> fitHeader());
        actionsRow.getStyleClass().add("note-actions");
        actionsRow.setAlignment(Pos.CENTER_RIGHT);
        actionsRow.setManaged(false);
        actionsRow.setVisible(false);
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
            } else if (QUICK_OPEN.match(e)) {
                quickOpen();
                e.consume();
            } else if (SEARCH.match(e)) {
                search();
                e.consume();
            } else if (PASTE.match(e) && note != null && !reading && editor.view.isFocused() && pasteImages()) {
                e.consume();
            }
        });
        icon = new Image(Objects.requireNonNull(NotesApp.class.getResourceAsStream("icon.png"), "icon.png"));
        stage.getIcons().add(icon);
        dark = vault.setting("theme").equals("dark");
        applyTheme();
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
        Button add = button("+ New notebook", "primary", e -> newNotebook());
        add.setTooltip(new Tooltip("Ctrl+N"));
        bar.getChildren().setAll(brand(), grow(), finder(), add, more(item("Search in all notes…", this::search),
                item("Open data folder", () -> showInFolder(vault.root)), new SeparatorMenuItem(), themeItem()));
        root.setCenter(homeContent(true));
        stage.setTitle(NAME);
        refreshTitles();
    }

    /** The icon and the name, at the left of Home's bar. */
    private Node brand() {
        ImageView logo = new ImageView(icon);
        logo.setFitWidth(22);
        logo.setFitHeight(22);
        logo.setSmooth(true);
        Label name = new Label(NAME);
        name.getStyleClass().add("app-name");
        HBox brand = new HBox(9, logo, name);
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    /** The field-like button of the bar that opens Quick open. */
    private Button finder() {
        SVGPath glass = new SVGPath();   // a magnifying glass, on 24 units
        glass.setContent("M10.5 3a7.5 7.5 0 0 1 5.96 12.06l4.24 4.24-1.41 1.41-4.24-4.24A7.5 7.5 0 1 1 10.5 3zm0 2a5.5 5.5 0 1 0 0 11a5.5 5.5 0 0 0 0-11z");
        glass.setFillRule(FillRule.EVEN_ODD);
        glass.getStyleClass().add("icon");
        glass.setScaleX(0.62);
        glass.setScaleY(0.62);
        Label text = new Label("Quick open…"), keys = new Label("Ctrl+O");
        text.getStyleClass().add("finder-text");
        keys.getStyleClass().add("finder-keys");
        HBox content = new HBox(8, new Group(glass), text, grow(), keys);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPrefWidth(236);
        Button find = new Button(null, content);
        find.getStyleClass().add("finder");
        find.setOnAction(e -> quickOpen());
        find.setFocusTraversable(false);   // focused, it would look like a field being typed in; Ctrl+O reaches it
        find.setTooltip(new Tooltip("Open any note by its title (Ctrl+O); search all their text with Ctrl+Shift+F"));
        return find;
    }

    private CheckMenuItem themeItem() {
        CheckMenuItem item = new CheckMenuItem("Dark theme");
        item.setSelected(dark);
        item.setOnAction(e -> setDark(item.isSelected()));
        return item;
    }

    void setDark(boolean dark) {
        this.dark = dark;
        applyTheme();
        try {
            vault.setSetting("theme", dark ? "dark" : "light");
        } catch (IOException ignored) {
            // The theme still changes; it just is not remembered for next time.
        }
    }

    private void applyTheme() {
        root.getStyleClass().remove("dark");
        if (dark) root.getStyleClass().add("dark");
        editor.setDark(dark);
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
            ImageView logo = new ImageView(icon);
            logo.setFitWidth(72);
            logo.setFitHeight(72);
            logo.setSmooth(true);
            VBox empty = placeholder("Welcome to Devava Notes", "A notebook keeps the notes of one subject, course or project.");
            empty.getChildren().add(0, logo);
            empty.getChildren().add(button("+ New notebook", "primary", e -> newNotebook()));
            return empty;
        }
        // On the left the notebooks; on the right what is coming and what was written last.
        FlowPane cards = new FlowPane(16, 16);
        cards.getChildren().setAll(notebooks.stream().map(this::card).toList());
        Label title = new Label("Notebooks");
        title.getStyleClass().add("page-title");
        VBox main = new VBox(18, title, cards);
        HBox.setHgrow(main, Priority.ALWAYS);
        VBox side = new VBox(10);
        side.setMinWidth(320);
        side.setPrefWidth(370);
        List<Upcoming> upcoming = upcoming(notebooks);
        if (!upcoming.isEmpty()) {
            side.getChildren().addAll(sectionTitle("UPCOMING"), panel(upcoming.stream().limit(8).map(this::upcomingRow).toList()));
        }
        List<Vault.Place> recent = recent(vault.everyNote(), 6);
        if (!recent.isEmpty()) {
            Label recentTitle = sectionTitle("RECENT");
            if (!side.getChildren().isEmpty()) VBox.setMargin(recentTitle, new Insets(16, 0, 0, 0));
            side.getChildren().addAll(recentTitle, panel(recent.stream().map(this::recentRow).toList()));
        }
        HBox home = new HBox(main);
        if (!side.getChildren().isEmpty()) home.getChildren().add(side);
        home.getStyleClass().add("home");
        ScrollPane scroll = new ScrollPane(home);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("home-scroll");
        return scroll;
    }

    private static VBox panel(List<? extends Node> rows) {
        VBox panel = new VBox(2);
        panel.getChildren().setAll(rows);
        panel.getStyleClass().add("panel");
        return panel;
    }

    /** Each type's color on Home: the mark and the name on its notebooks' cards. */
    private static String color(NotebookType type) {
        return switch (type.id()) {
            case "class-notes" -> "#0D9488";
            case "competitive-programming" -> "#EA580C";
            default -> "#6366F1";
        };
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
        NotebookView.colored(when, b == null ? "" : b.color());
        when.setMinWidth(110);
        title.getStyleClass().add("row-title");
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

    /** The notes changed last, in any notebook. */
    private static List<Vault.Place> recent(List<Vault.Place> all, int count) {
        return all.stream().sorted(Comparator.comparing((Vault.Place p) -> modified(p.note())).reversed()).limit(count).toList();
    }

    private Button recentRow(Vault.Place p) {
        Label when = new Label(ago(modified(p.note()))), title = new Label(p.title()), where = new Label(p.notebook().getFileName().toString());
        when.getStyleClass().add("card-meta");
        when.setMinWidth(110);
        title.getStyleClass().add("row-title");
        where.getStyleClass().add("card-meta");
        HBox line = new HBox(12, when, title, where);
        line.setAlignment(Pos.CENTER_LEFT);
        Button row = new Button(null, line);
        row.getStyleClass().add("recent-row");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setOnAction(e -> go(p));
        return row;
    }

    private static FileTime modified(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /** "Just now", "12 minutes ago", "3 hours ago", "Yesterday", or the day. */
    private static String ago(FileTime time) {
        long minutes = ChronoUnit.MINUTES.between(time.toInstant(), Instant.now());
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        if (minutes < 60 * 24) return minutes < 120 ? "1 hour ago" : minutes / 60 + " hours ago";
        LocalDate day = LocalDate.ofInstant(time.toInstant(), ZoneId.systemDefault());
        return day.equals(LocalDate.now().minusDays(1)) ? "Yesterday" : DAY.format(time.toInstant());
    }

    private static Label sectionTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("section-title");
        return title;
    }

    private Button card(Vault.Notebook nb) {
        Region mark = new Region();
        mark.getStyleClass().add("type-mark");
        mark.setStyle("-fx-background-color: " + color(nb.type()) + ";");
        Label name = new Label(nb.name());
        name.getStyleClass().add("card-title");
        Label kind = new Label(nb.type().name());
        kind.getStyleClass().add("card-type");
        NotebookView.colored(kind, color(nb.type()));
        Label meta = new Label(count(nb.notes()) + " · edited " + DAY.format(nb.edited().toInstant()));
        meta.getStyleClass().add("card-meta");
        VBox body = new VBox(4, mark, name, kind, meta);
        VBox.setMargin(name, new Insets(8, 0, 0, 0));
        Button card = new Button(null, body);
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
        List<MenuItem> menu = new ArrayList<>(List.of(notebookItems(dir)));
        menu.addAll(List.of(new SeparatorMenuItem(), themeItem()));
        bar.getChildren().setAll(button("‹ Home", "flat", e -> showHome()), name, typeName, grow(), finder(), newButton(),
                more(menu.toArray(MenuItem[]::new)));
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
        refreshTitles();
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
        editor.open(n.body, p.getParent());
        notePane.setTop(new VBox(noteHeader, actionsRow, properties.node));
        notePane.setCenter(editor.view);
        browser.select(p);
        fitHeader();
    }

    /** Buttons cut short to "…" say nothing: when the title leaves them no room, they go below it. */
    private void fitHeader() {
        double width = noteHeader.getWidth(), needed = noteTitle.prefWidth(-1) + kindName.prefWidth(-1) + badge.prefWidth(-1)
                + actions.prefWidth(-1) + 4 * noteHeader.getSpacing() + noteHeader.getInsets().getLeft() + noteHeader.getInsets().getRight();
        boolean narrow = width > 0 && needed > width;
        if (narrow == (actions.getParent() == actionsRow)) return;
        (narrow ? actionsRow : noteHeader).getChildren().add(actions);   // leaving the other row
        actionsRow.setManaged(narrow);
        actionsRow.setVisible(narrow);
    }

    /** A problem's difficulty in its judge's color, and its level when that says more ("800 · Easy"). */
    private void showBadge() {
        Badge b = current == null ? null : type.badge(current);
        String level = current == null ? "" : Judges.level(current);
        badge.setText(b == null ? "" : b.text().equalsIgnoreCase(level) || level.isEmpty() ? b.text() : b.text() + " · " + level);
        NotebookView.colored(badge, b == null ? "" : b.color());
        fitHeader();
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
        refreshTitles();
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
                    fitHeader();
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
        Runnable narrow = () -> {
            String wanted = NotebookView.fold(filter.getText().strip());
            list.getItems().setAll(snippets.stream().filter(e -> NotebookView.fold(e.title() + " "
                    + String.join(" ", e.note().list("algorithms")) + " " + String.join(" ", e.note().list("techniques"))).contains(wanted)).toList());
            list.getSelectionModel().selectFirst();
        };
        filter.textProperty().addListener((o, was, now) -> narrow.run());
        narrow.run();
        pick("Insert snippet", "Insert", filter, list).ifPresent(e -> insertSnippet(e.path()));
    }

    /** Ctrl+O: any note of any notebook by a part of its title; with nothing typed, the latest first. */
    void quickOpen() {
        List<Vault.Place> all = vault.everyNote();
        Map<Path, FileTime> times = new HashMap<>();
        all.forEach(p -> times.put(p.note(), modified(p.note())));
        TextField title = new TextField();
        title.setPromptText("Title");
        ListView<Vault.Place> list = new ListView<>();
        list.setCellFactory(v -> placeCell(p -> p.notebook().getFileName().toString()));
        Runnable narrow = () -> {
            String wanted = NotebookView.fold(title.getText().strip());
            list.getItems().setAll(all.stream().filter(p -> NotebookView.fold(p.title()).contains(wanted))
                    .sorted(Comparator.comparing((Vault.Place p) -> !NotebookView.fold(p.title()).startsWith(wanted))
                            .thenComparing((Vault.Place p) -> times.get(p.note()), Comparator.reverseOrder()))
                    .limit(200).toList());
            list.getSelectionModel().selectFirst();
        };
        title.textProperty().addListener((o, was, now) -> narrow.run());
        narrow.run();
        pick("Quick open", "Open", title, list).ifPresent(this::go);
    }

    /** A line found by Search: the note and the line where the words are. */
    private record Hit(Vault.Place place, String line) {
    }

    /** Ctrl+Shift+F: every note with the words in its text or properties, accents and case aside. */
    void search() {
        record Text(Vault.Place place, String text, String folded) {
        }
        List<Text> texts = new ArrayList<>();
        for (Vault.Place p : vault.everyNote()) {
            try {
                String text = Vault.read(p.note()).replace("\r\n", "\n");
                texts.add(new Text(p, text, foldEachChar(text)));
            } catch (IOException ignored) {
                // Unreadable right now: not searched.
            }
        }
        TextField words = new TextField();
        words.setPromptText("Words to find");
        ListView<Hit> list = new ListView<>();
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Hit hit, boolean empty) {
                super.updateItem(hit, empty);
                setText(null);
                if (empty || hit == null) {
                    setGraphic(null);
                    return;
                }
                Label title = new Label(hit.place().title() + "  ·  " + hit.place().notebook().getFileName()), line = new Label(hit.line());
                line.getStyleClass().add("note-subtitle");
                setGraphic(new VBox(1, title, line));
            }
        });
        PauseTransition typing = new PauseTransition(Duration.millis(150));
        typing.setOnFinished(e -> {
            String wanted = foldEachChar(words.getText().strip());
            List<Hit> hits = new ArrayList<>();
            if (wanted.length() >= 2) {
                for (Text t : texts) {
                    int at = t.folded().indexOf(wanted);
                    if (at >= 0) hits.add(new Hit(t.place(), around(t.text(), at, wanted.length())));
                    if (hits.size() == 200) break;
                }
            }
            list.getItems().setAll(hits);
            list.getSelectionModel().selectFirst();
        });
        words.textProperty().addListener((o, was, now) -> typing.playFromStart());
        pick("Search in all notes", "Open", words, list).ifPresent(hit -> {
            go(hit.place());
            if (!hit.place().note().equals(note)) return;
            setReading(false);
            editor.selectMatch(words.getText().strip());
        });
    }

    /** The line of the text with the match, cut around it when long. */
    private static String around(String text, int at, int length) {
        int start = text.lastIndexOf('\n', at) + 1, end = text.indexOf('\n', at);
        String line = text.substring(start, end < 0 ? text.length() : end).strip();
        int in = line.indexOf(text.substring(at, at + length));
        return line.length() <= 110 || in < 0 ? line : (in > 40 ? "…" : "") + line.substring(Math.max(0, in - 40), Math.min(line.length(), in + 70)) + "…";
    }

    /** Accents and case aside, keeping every position (so a match found here is at the same place in the text). */
    private static String foldEachChar(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String f = c < 128 ? String.valueOf(Character.toLowerCase(c)) : NotebookView.fold(String.valueOf(c));
            out.append(f.isEmpty() ? c : f.charAt(0));
        }
        return out.toString();
    }

    /** Opens a note wherever it is, going to its notebook first. */
    private void go(Vault.Place place) {
        if (!place.notebook().equals(notebook)) openNotebook(place.notebook());
        if (!place.notebook().equals(notebook)) return;   // its current note could not be left
        openNote(place.note());
        browser.reveal(place.note());
    }

    /** A [[link]]: the note with that title (in this notebook first), or else a new one here. */
    private void openTitle(String title) {
        Optional<Vault.Place> found = vault.everyNote().stream().filter(p -> p.title().equalsIgnoreCase(title))
                .min(Comparator.comparing((Vault.Place p) -> !p.notebook().equals(notebook)));
        if (found.isPresent()) {
            go(found.get());
        } else if (notebook != null) {
            try {
                createNote(type.kinds().get(0), title, "");
            } catch (IOException e) {
                error("Couldn't create “" + title + "”", reason(e));
            }
        }
    }

    /** The titles [[ offers while writing, from every notebook. */
    private void refreshTitles() {
        editor.setTitles(vault.everyNote().stream().map(Vault.Place::title).distinct().sorted(Vault::compareNatural).toList());
    }

    /** Ctrl+V with an image (a screenshot, "Copy image") or image files: into attachments/ and the note. */
    private boolean pasteImages() {
        Clipboard clip = Clipboard.getSystemClipboard();
        try {
            if (clip.hasFiles() && clip.getFiles().stream().anyMatch(f -> Vault.isImage(f.toPath()))) {
                attachImages(clip.getFiles());
                return true;
            }
            if (clip.hasImage() && !clip.hasString()) {   // text copied with a picture of it (a spreadsheet) pastes as text
                editor.insert(imageLink(Vault.attach(notebook, png(clip.getImage()))));
                return true;
            }
        } catch (IOException e) {
            error("Couldn't paste the image", reason(e));
            return true;
        }
        return false;
    }

    /** The open note, for the tests. */
    Path openPath() {
        return note;
    }

    void attachImages(List<File> files) throws IOException {
        StringBuilder links = new StringBuilder();
        for (File f : files) {
            if (Vault.isImage(f.toPath())) links.append(imageLink(Vault.attach(notebook, f.toPath())));
        }
        editor.insert(links.toString());
    }

    private static String imageLink(Path image) {
        return "![](<attachments/" + image.getFileName() + ">)\n";
    }

    private static byte[] png(Image image) throws IOException {
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] argb = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        BufferedImage picture = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        picture.setRGB(0, 0, w, h, argb, 0, w);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", out);
        return out.toByteArray();
    }

    private static ListCell<Vault.Place> placeCell(Function<Vault.Place, String> where) {
        return new ListCell<>() {
            @Override
            protected void updateItem(Vault.Place p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.title() + "  ·  " + where.apply(p));
            }
        };
    }

    /** A dialog to pick one line of a list narrowed by typing; ↑ ↓ move, Enter or a double click picks. */
    private <T> Optional<T> pick(String title, String action, TextField field, ListView<T> list) {
        list.setPrefSize(600, 380);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        style(dialog);
        ButtonType ok = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(8, field, list));
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ok);
        okButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) list.getSelectionModel().selectNext();
            else if (e.getCode() == KeyCode.UP) list.getSelectionModel().selectPrevious();
            else return;
            list.scrollTo(list.getSelectionModel().getSelectedIndex());
            e.consume();
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) okButton.fire();
        });
        Platform.runLater(field::requestFocus);
        return dialog.showAndWait().filter(b -> b == ok).map(b -> list.getSelectionModel().getSelectedItem());
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
        SVGPath dots = new SVGPath();   // three dots, on 24 units
        dots.setContent("M5 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4zm7 0a2 2 0 1 0 0 4a2 2 0 1 0 0-4zm7 0a2 2 0 1 0 0 4a2 2 0 1 0 0-4z");
        dots.getStyleClass().add("icon");
        MenuButton more = new MenuButton(null, dots, items);
        more.setTooltip(new Tooltip("More"));
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
        if (dark) dialog.getDialogPane().getStyleClass().add("dark");
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
