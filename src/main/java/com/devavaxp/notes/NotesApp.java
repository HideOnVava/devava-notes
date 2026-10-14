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
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
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
import javafx.scene.control.ToggleButton;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
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
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    /** Editar or Vista previa: one is always pressed, and Ctrl+E switches. */
    private final ToggleButton editTab = new ToggleButton("Editar"), readTab = new ToggleButton("Vista previa");
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
    /** Home shows the notebooks of earlier school periods only when asked. */
    private boolean showOlder;
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
        Locale.setDefault(Text.SPANISH);   // the stock dialogs' "Cancelar" and the calendars too
        this.stage = stage;
        this.vault = vault;
        editor = new Editor(this::edited, this::openLink, this::openTitle, tag -> browser.showTag(tag), this::paste);
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
                error("No se pudo agregar la imagen", reason(ex));
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
        kindName.getStyleClass().add("kind");
        status.getStyleClass().add("status-error");
        editTab.setGraphic(Icons.of("pen", 15));
        readTab.setGraphic(Icons.of("eye", 15));
        editTab.setTooltip(new Tooltip("Escribir la nota (Ctrl+E)"));
        readTab.setTooltip(new Tooltip("Ver cómo queda: fórmulas, código e imágenes (Ctrl+E)"));
        editTab.setOnAction(e -> setReading(false));
        readTab.setOnAction(e -> setReading(true));
        HBox modes = segmented(editTab, readTab);
        modes.setMinWidth(Region.USE_PREF_SIZE);
        editTab.setSelected(true);
        actions.getChildren().setAll(status, tools, modes);
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
        notebookView.setDividerPositions(0.19, 0.45);
        browser.tableMode().addListener((o, was, table) -> notebookView.setDividerPositions(0.19, table ? 0.68 : 0.45));
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
        Button add = button("Nuevo cuaderno", "plus", "primary", e -> newNotebook());
        add.setTooltip(new Tooltip("Ctrl+N"));
        bar.getChildren().setAll(brand(), grow(), finder(), add, help(), options(
                item("Buscar en todas las notas…", this::search),
                item("Importar cuaderno…", this::importNotebook),
                item("Abrir la carpeta de datos", () -> showInFolder(vault.root)),
                new SeparatorMenuItem(), themeItem()));
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
        Label text = new Label("Buscar una nota…"), keys = new Label("Ctrl+O");
        text.getStyleClass().add("finder-text");
        keys.getStyleClass().add("finder-keys");
        HBox content = new HBox(8, Icons.of("search", 16), text, grow(), keys);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPrefWidth(250);
        Button find = new Button(null, content);
        find.getStyleClass().add("finder");
        find.setOnAction(e -> quickOpen());
        find.setFocusTraversable(false);   // focused, it would look like a field being typed in; Ctrl+O reaches it
        find.setTooltip(new Tooltip("Abre cualquier nota por su título (Ctrl+O); busca en todo su texto con Ctrl+Shift+F"));
        return find;
    }

    private Button help() {
        Button help = button("Ayuda", "help", "flat", e -> showHelp());
        help.setTooltip(new Tooltip("Atajos de teclado y dónde quedan tus notas"));
        return help;
    }

    /** The keyboard shortcuts, and where the notes are kept. */
    void showHelp() {
        String[][] keys = {
                {"Ctrl+N", "Nuevo cuaderno en Inicio; nueva nota dentro de un cuaderno"},
                {"Ctrl+O", "Abrir cualquier nota por su título"},
                {"Ctrl+Shift+F", "Buscar en el texto de todas las notas"},
                {"Ctrl+E", "Pasar entre Editar y Vista previa"},
                {"Ctrl+F", "Buscar y reemplazar dentro de la nota"},
                {"Ctrl+V", "Pegar texto, o una imagen como una captura de pantalla"},
                {"[[", "Enlazar otra nota: escribe [[ y elige su título"},
                {"Ctrl+clic", "Abrir el [[enlace]] que está bajo el ratón"},
                {"#tema", "Etiquetar la nota desde su texto"}};
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(9);
        for (int i = 0; i < keys.length; i++) {
            Label key = new Label(keys[i][0]), what = new Label(keys[i][1]);
            key.getStyleClass().add("key");
            grid.addRow(i, key, what);
        }
        Label where = new Label("Tus notas se guardan en " + vault.root + ", como archivos de texto (Markdown) que también abren otros programas.");
        where.setWrapText(true);
        where.setMaxWidth(480);
        where.getStyleClass().add("hint");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ayuda");
        dialog.setHeaderText("Atajos de teclado");
        style(dialog);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(new VBox(18, grid, where));
        dialog.showAndWait();
    }

    private CheckMenuItem themeItem() {
        CheckMenuItem item = new CheckMenuItem("Tema oscuro");
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
            if (showErrors) error("No se pudieron abrir tus notas", vault.root + "\n\n" + reason(e));
        }
        if (notebooks.isEmpty()) {
            ImageView logo = new ImageView(icon);
            logo.setFitWidth(72);
            logo.setFitHeight(72);
            logo.setSmooth(true);
            VBox empty = placeholder("Te damos la bienvenida a Devava Notes",
                    "Crea un cuaderno para cada materia del semestre: ahí van tus apuntes, tareas, exámenes y código.");
            empty.getChildren().add(0, logo);
            empty.getChildren().add(button("Nuevo cuaderno", "plus", "primary", e -> newNotebook()));
            return empty;
        }
        // On the left the notebooks by school period, the newest first, earlier periods folded away;
        // on the right what is coming and what was written last.
        Period now = Period.of(LocalDate.now());
        Map<Period, List<Vault.Notebook>> byPeriod = new TreeMap<>(Comparator.reverseOrder());
        notebooks.forEach(nb -> byPeriod.computeIfAbsent(nb.period(), k -> new ArrayList<>()).add(nb));
        VBox main = new VBox(30), older = new VBox(30);
        int olderCount = 0;
        for (Map.Entry<Period, List<Vault.Notebook>> group : byPeriod.entrySet()) {
            boolean past = group.getKey().compareTo(now) < 0;
            (past ? older : main).getChildren().add(periodSection(group.getKey(), group.getValue()));
            if (past) olderCount += group.getValue().size();
        }
        if (main.getChildren().isEmpty()) {
            VBox none = placeholder("Aún no tienes cuadernos de " + now, "Crea uno para cada materia de este semestre.");
            none.getChildren().add(button("Nuevo cuaderno", "plus", "primary", e -> newNotebook()));
            main.getChildren().add(none);
        }
        if (!older.getChildren().isEmpty()) {
            Button toggle = button("Semestres anteriores · " + countNotebooks(olderCount), showOlder ? "down" : "right", "flat", e -> {
                showOlder = !showOlder;
                root.setCenter(homeContent(false));
            });
            toggle.getStyleClass().add("older-toggle");
            main.getChildren().add(toggle);
            if (showOlder) main.getChildren().add(older);
        }
        HBox.setHgrow(main, Priority.ALWAYS);
        VBox side = new VBox(10);
        side.setMinWidth(320);
        side.setPrefWidth(370);
        List<Upcoming> upcoming = upcoming(notebooks);
        if (!upcoming.isEmpty()) {
            side.getChildren().addAll(sectionTitle("LO QUE SE ACERCA"), panel(upcoming.stream().limit(8).map(this::upcomingRow).toList()));
        }
        List<Vault.Place> recent = recent(vault.everyNote(), 6);
        if (!recent.isEmpty()) {
            Label recentTitle = sectionTitle("SEGUIR DONDE TE QUEDASTE");
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

    /** A school period on Home: its name, how many notebooks it has, and their cards. */
    private Node periodSection(Period period, List<Vault.Notebook> notebooks) {
        Label name = new Label(period.toString()), count = new Label(countNotebooks(notebooks.size()));
        name.getStyleClass().add("period-title");
        count.getStyleClass().add("period-count");
        HBox head = new HBox(10, name, count);
        head.setAlignment(Pos.BASELINE_LEFT);
        FlowPane cards = new FlowPane(14, 14);
        cards.setRowValignment(VPos.TOP);
        cards.getChildren().setAll(notebooks.stream().map(this::card).toList());
        return new VBox(12, head, cards);
    }

    private static String countNotebooks(int n) {
        return n == 1 ? "1 cuaderno" : n + " cuadernos";
    }

    private static VBox panel(List<? extends Node> rows) {
        VBox panel = new VBox(2);
        panel.getChildren().setAll(rows);
        panel.getStyleClass().add("panel");
        return panel;
    }

    /** Each type's color: its notebooks' marks, and their type's name. */
    private static String color(NotebookType type) {
        return switch (type.id()) {
            case "class-notes" -> "#0F8A80";
            case "competitive-programming" -> "#D9590B";
            case "databases" -> "#0377B5";
            default -> "#5D6B82";
        };
    }

    /** Each type's icon. */
    private static String icon(NotebookType type) {
        return switch (type.id()) {
            case "class-notes" -> "book";
            case "competitive-programming" -> "trophy";
            case "databases" -> "database";
            default -> "note";
        };
    }

    /** A type's mark: its icon, white on its color. */
    private static Node tile(NotebookType type, double size) {
        StackPane tile = new StackPane(Icons.of(icon(type), Math.round(size * 0.55)));
        tile.getStyleClass().add("tile");
        tile.setStyle("-fx-background-color: " + color(type) + ";");
        tile.setMinSize(size, size);
        tile.setMaxSize(size, size);
        return tile;
    }

    /** A pending assignment or a coming exam, in one of the Class Notes notebooks. */
    private record Upcoming(Vault.Notebook notebook, Path path, Note note) {
    }

    /** What is still to do in every course: late assignments first, then by day. */
    private static List<Upcoming> upcoming(List<Vault.Notebook> notebooks) {
        LocalDate today = LocalDate.now();
        List<Upcoming> items = new ArrayList<>();
        for (Vault.Notebook nb : notebooks) {
            if (!nb.type().course()) continue;
            List<Path> notes;
            try {
                notes = Vault.notes(nb.dir());
            } catch (IOException e) {
                continue;   // a course that cannot be read right now has nothing to show
            }
            for (Path p : notes) {
                try {
                    Note n = Note.parse(Vault.read(p));
                    if (Agenda.upcoming(n, today)) items.add(new Upcoming(nb, p, n));
                } catch (IOException ignored) {
                    // Held for a moment (OneDrive syncing it, an antivirus): the others still show.
                }
            }
        }
        items.sort(Comparator.comparing((Upcoming u) -> Agenda.when(u.note())).thenComparing(u -> Vault.title(u.path()), Vault::compareNatural));
        return items;
    }

    private Button upcomingRow(Upcoming u) {
        Badge b = Agenda.badge(u.note(), LocalDate.now());
        Label when = new Label(b == null ? "" : b.text());
        NotebookView.colored(when, b == null ? "" : b.color());
        Button row = new Button(null, homeRow(when, Vault.title(u.path()), u.notebook().name()));
        row.getStyleClass().add("upcoming-row");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setOnAction(e -> {
            openNotebook(u.notebook().dir());
            openNote(u.path());
            browser.reveal(u.path());
        });
        return row;
    }

    /** A row of Home's lists: when, in a column of its own, then the note's title over its notebook's name. */
    private static HBox homeRow(Label when, String title, String notebook) {
        Label name = new Label(title), where = new Label(notebook);
        name.getStyleClass().add("row-title");
        where.getStyleClass().add("card-meta");
        when.setMinWidth(96);
        HBox line = new HBox(12, when, new VBox(1, name, where));
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    /** The notes changed last, in any notebook. */
    private static List<Vault.Place> recent(List<Vault.Place> all, int count) {
        return all.stream().sorted(Comparator.comparing((Vault.Place p) -> modified(p.note())).reversed()).limit(count).toList();
    }

    private Button recentRow(Vault.Place p) {
        Label when = new Label(ago(modified(p.note())));
        when.getStyleClass().add("card-meta");
        Button row = new Button(null, homeRow(when, p.title(), p.notebook().getFileName().toString()));
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

    /** "Ahora mismo", "Hace 12 minutos", "Hace 3 horas", "Ayer", or the day. */
    private static String ago(FileTime time) {
        long minutes = ChronoUnit.MINUTES.between(time.toInstant(), Instant.now());
        if (minutes < 1) return "Ahora mismo";
        if (minutes < 60) return minutes == 1 ? "Hace 1 minuto" : String.format("Hace %d minutos", minutes);
        if (minutes < 60 * 24) return minutes < 120 ? "Hace 1 hora" : String.format("Hace %d horas", minutes / 60);
        LocalDate day = LocalDate.ofInstant(time.toInstant(), ZoneId.systemDefault());
        return day.equals(LocalDate.now().minusDays(1)) ? "Ayer" : day().format(time.toInstant());
    }

    /** "Sep 28, 2026", "28 sept 2026". */
    private static DateTimeFormatter day() {
        return DateTimeFormatter.ofPattern("d MMM yyyy", Text.SPANISH).withZone(ZoneId.systemDefault());
    }

    private static Label sectionTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("section-title");
        return title;
    }

    private Button card(Vault.Notebook nb) {
        Label name = new Label(nb.name());
        name.getStyleClass().add("card-title");
        name.setWrapText(true);
        HBox top = new HBox(11, tile(nb.type(), 36), name);
        top.setAlignment(Pos.CENTER_LEFT);
        Label kind = new Label(nb.type().name());
        kind.getStyleClass().add("card-type");
        NotebookView.colored(kind, color(nb.type()));
        String when = ago(nb.edited());
        Label meta = new Label(count(nb.notes()) + " · " + when.substring(0, 1).toLowerCase(Text.SPANISH) + when.substring(1));
        meta.getStyleClass().add("card-meta");
        Button card = new Button(null, new VBox(10, top, new VBox(2, kind, meta)));
        card.getStyleClass().add("card");
        card.setWrapText(true);   // so its height is measured at its width, where the name wraps
        card.setOnAction(e -> openNotebook(nb.dir()));
        card.setContextMenu(new ContextMenu(notebookItems(nb.dir())));
        return card;
    }

    private MenuItem[] notebookItems(Path dir) {
        List<MenuItem> items = new ArrayList<>(List.of(
                item("Cambiar nombre…", () -> renameNotebook(dir)),
                item("Cambiar tipo…", () -> changeType(dir)),
                item("Cambiar periodo…", () -> changePeriod(dir))));
        if (Vault.type(dir).equals(NotebookType.COMPETITIVE_PROGRAMMING)) items.add(item("Plantilla de C++…", () -> editTemplate(dir)));
        items.addAll(List.of(
                item("Exportar como ZIP…", () -> exportNotebook(dir)),
                item("Mostrar en la carpeta", () -> showInFolder(dir)),
                new SeparatorMenuItem(),
                danger(item("Mover a la papelera…", () -> trashNotebook(dir)))));
        return items.toArray(MenuItem[]::new);
    }

    /** The school period a notebook shows under on Home. */
    private void changePeriod(Path dir) {
        Period set = Period.parse(Vault.meta(dir, "period"));
        ComboBox<Period> periods = periodChoice(set == null ? Period.of(LocalDate.now()) : set);
        Label about = new Label("En Inicio, el cuaderno aparece junto con las materias de ese semestre.");
        about.getStyleClass().add("hint");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Cambiar periodo");
        dialog.setHeaderText("Periodo de “" + dir.getFileName() + "”");
        style(dialog);
        ButtonType ok = new ButtonType("Cambiar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(12, periods, about));
        if (dialog.showAndWait().filter(b -> b == ok).isEmpty()) return;
        try {
            Vault.setMeta(dir, "period", periods.getValue().toString());
            if (notebook == null) root.setCenter(homeContent(false));
        } catch (IOException e) {
            error("No se pudo cambiar el periodo", reason(e));
        }
    }

    /** The periods to pick from: the next one, this one and the ones of the last two years. */
    private static ComboBox<Period> periodChoice(Period selected) {
        ComboBox<Period> box = new ComboBox<>();
        Period p = Period.of(LocalDate.now()).next();
        for (int i = 0; i < 8; i++, p = p.previous()) box.getItems().add(p);
        if (!box.getItems().contains(selected)) box.getItems().add(selected);
        box.setValue(selected);
        return box;
    }

    /** Saves the notebook as one ZIP to share it; whoever gets it adds it with Import notebook… on Home. */
    private void exportNotebook(Path dir) {
        if (dir.equals(notebook)) save();   // the open note, as it is now
        FileChooser chooser = new FileChooser();
        chooser.setTitle(String.format("Exportar “%s”", dir.getFileName()));
        chooser.setInitialFileName(dir.getFileName() + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        File zip = chooser.showSaveDialog(stage);
        if (zip == null) return;
        try {
            Vault.exportZip(dir, zip.toPath());
        } catch (IOException e) {
            error("No se pudo exportar el cuaderno", reason(e));
        }
    }

    /** Adds a notebook someone shared as a ZIP, and opens it. */
    private void importNotebook() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar un cuaderno");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        File zip = chooser.showOpenDialog(stage);
        if (zip == null) return;
        try {
            openNotebook(vault.importZip(zip.toPath()));
        } catch (IOException e) {
            error("No se pudo importar el cuaderno", reason(e));
        }
    }

    /** The C++ that new problems and "+ Solution" start with, kept in the notebook's .notebook.json. */
    private void editTemplate(Path dir) {
        TextArea code = new TextArea(template(dir));
        code.getStyleClass().add("code-area");
        code.setPrefColumnCount(64);
        code.setPrefRowCount(18);
        Label about = new Label("Los problemas nuevos y + Solución empiezan con ella (los de LeetCode empiezan vacíos).");
        about.getStyleClass().add("type-description");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Plantilla de C++");
        dialog.setHeaderText("Plantilla de C++");
        style(dialog);
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(8, about, code));
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        try {
            Vault.setMeta(dir, "cpp", code.getText());
        } catch (IOException e) {
            error("No se pudo guardar la plantilla", reason(e));
        }
    }

    private static String template(Path dir) {
        String own = Vault.meta(dir, "cpp");
        return own.isBlank() ? Judges.CPP : own.stripTrailing();
    }

    void newNotebook() {
        askNotebook("Nuevo cuaderno", NotebookType.GENERAL, true).ifPresent(choice -> {
            try {
                Path dir = createNotebook(choice.name(), choice.type());
                if (choice.period() != null) Vault.setMeta(dir, "period", choice.period().toString());
                openNotebook(dir);
            } catch (IOException e) {
                error("No se pudo crear el cuaderno", reason(e));
            }
        });
    }

    Path createNotebook(String name, NotebookType type) throws IOException {
        return vault.createNotebook(name, type);
    }

    private void renameNotebook(Path dir) {
        ask("Cambiar nombre del cuaderno", "Nombre", dir.getFileName().toString()).ifPresent(name -> {
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
                error("No se pudo cambiar el nombre del cuaderno", reason(e));
            }
        });
    }

    private void changeType(Path dir) {
        askNotebook(String.format("Cambiar “%s” a", dir.getFileName()), Vault.type(dir), false).ifPresent(choice -> {
            boolean open = dir.equals(notebook);
            if (open && !leaveNote()) return;
            try {
                Vault.setType(dir, choice.type());
                if (open) openNotebook(dir);
                else showHome();
            } catch (IOException e) {
                error("No se pudo cambiar el tipo", reason(e));
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
        if (!confirm(String.format("¿Mover “%s” a la papelera?", name), notes == 0 ? "El cuaderno va a la papelera, donde puedes restaurarlo."
                : String.format("El cuaderno y sus %s van a la papelera, donde puedes restaurarlos.", count(notes)), "Mover a la papelera")) return;
        if (dir.equals(notebook) && !leaveNote()) return;
        try {
            Vault.trash(dir);
            note = null;
            showHome();
        } catch (IOException e) {
            error(String.format("No se pudo mover “%s” a la papelera", name), reason(e));
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
        typeName.getStyleClass().add("type-chip");
        NotebookView.colored(typeName, color(type));
        List<MenuItem> menu = new ArrayList<>(List.of(notebookItems(dir)));
        menu.addAll(List.of(new SeparatorMenuItem(), themeItem()));
        HBox title = new HBox(10, tile(type, 26), name, typeName);
        title.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().setAll(button("Inicio", "back", "flat", e -> showHome()), title, grow(), finder(), newButton(), help(),
                options(menu.toArray(MenuItem[]::new)));
        loadEntries(true);
        browser.open(type, entries);
        root.setCenter(notebookView);
        stage.setTitle(dir.getFileName() + " — " + NAME);
    }

    /** "Nueva nota" for one kind of note; with several, a menu that says what each kind is for (Ctrl+N makes the first). */
    private Node newButton() {
        Kind first = type.kinds().get(0);
        if (type.kinds().size() == 1) {
            Button add = button(first.create(), "plus", "primary", e -> newNote(first));
            add.setTooltip(new Tooltip("Ctrl+N"));
            return add;
        }
        MenuButton add = new MenuButton("Nueva nota", Icons.of("plus", 16));
        for (Kind k : type.kinds()) {
            Label name = new Label(k.name()), about = new Label(k.about());
            name.getStyleClass().add("menu-title");
            about.getStyleClass().add("menu-about");
            MenuItem item = new MenuItem(null, new VBox(1, name, about));
            item.setOnAction(e -> newNote(k));
            add.getItems().add(item);
        }
        add.getStyleClass().add("primary");
        add.setTooltip(new Tooltip("Ctrl+N: " + first.name().toLowerCase(Text.SPANISH)));
        return add;
    }

    /** Reads the properties of every note in the notebook; the open note keeps its own (maybe unsaved) ones. */
    private void loadEntries(boolean showErrors) {
        List<Path> paths;
        try {
            paths = Vault.notes(notebook);
        } catch (IOException e) {
            if (showErrors) error("No se pudieron listar las notas", reason(e));
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
            error(String.format("No se pudo abrir “%s”", Vault.title(p)), reason(e));
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
        if (type.equals(NotebookType.COMPETITIVE_PROGRAMMING) && kind.id().equals("problem")) {
            tools.getChildren().add(button("Solución", "plus", "flat", e -> newSolution()));
        }
        if (type.equals(NotebookType.DATABASES)) {
            tools.getChildren().add(button("Consulta", "plus", "flat", e -> {
                setReading(false);
                editor.insertCode("", "plsql");
            }));
        }
        if (type.kinds().stream().anyMatch(k -> k.id().equals("snippet"))) {
            tools.getChildren().add(button("Insertar fragmento…", "snippet", "flat", e -> insertSnippet()));
        }
        properties.show(kind, n);
        editor.open(n.body, p.getParent());
        notePane.setTop(new VBox(noteHeader, actionsRow, properties.node));
        notePane.setCenter(editor.view);
        browser.select(p);
        fitHeader();
    }

    /**
     * Buttons cut short to "…" say nothing: when the title leaves them no room, they go below it,
     * and when not even that row is wide enough, the tools keep only their icons (and say what they do on hover).
     */
    private void fitHeader() {
        notePane.applyCss();   // buttons just made measure their real size only once styled
        tools.getChildren().forEach(n -> ((Button) n).setContentDisplay(ContentDisplay.LEFT));
        double width = noteHeader.getWidth(), needed = noteTitle.prefWidth(-1) + kindName.prefWidth(-1) + badge.prefWidth(-1)
                + actions.prefWidth(-1) + 4 * noteHeader.getSpacing() + noteHeader.getInsets().getLeft() + noteHeader.getInsets().getRight();
        boolean narrow = width > 0 && needed > width;
        if (narrow != (actions.getParent() == actionsRow)) {
            (narrow ? actionsRow : noteHeader).getChildren().add(actions);   // leaving the other row
            actionsRow.setManaged(narrow);
            actionsRow.setVisible(narrow);
        }
        boolean tight = narrow && actions.prefWidth(-1) + actionsRow.getInsets().getLeft() + actionsRow.getInsets().getRight() > width;
        for (Node n : tools.getChildren()) {
            Button b = (Button) n;
            b.setContentDisplay(tight ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
            b.setTooltip(tight ? new Tooltip(b.getText()) : null);
        }
    }

    /** A problem's difficulty in its judge's color, and its level when that says more ("800 · Easy"). */
    private void showBadge() {
        Badge b = current == null ? null : type.badge(current);
        String level = current == null ? "" : Text.shown(Judges.level(current));
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
        notePane.setCenter(placeholder("Ninguna nota abierta", "Elige una nota o pulsa Ctrl+N para escribir una nueva."));
    }

    void newNote(Kind kind) {
        if (notebook == null) return;
        boolean hasLink = kind.fields().stream().anyMatch(f -> f.key().equals("url"));
        Optional<String[]> answer = hasLink ? askProblem()
                : ask(kind.create(), "Título", "").map(title -> new String[]{title, ""});
        answer.ifPresent(a -> {
            try {
                createNote(kind, a[0], a[1]);
            } catch (IOException e) {
                error("No se pudo crear la nota", reason(e));
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
        ask("Cambiar nombre de la nota", "Título", Vault.title(p)).ifPresent(title -> {
            boolean open = p.equals(note);
            if (open && !save()) {
                error("No se pudo cambiar el nombre de la nota", String.format("Sus últimos cambios no están guardados: %s", saveError));
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
                error("No se pudo cambiar el nombre de la nota", reason(e));
            }
        });
    }

    void trashNote(Path p) {
        if (!confirm(String.format("¿Mover “%s” a la papelera?", Vault.title(p)), "Puedes restaurarla desde la papelera.", "Mover a la papelera")) return;
        boolean open = p.equals(note);
        if (open) save();   // its latest text goes to the trash with it
        try {
            Vault.trash(p);
            if (open) closeNote();
            loadEntries(true);
            browser.update();
        } catch (IOException e) {
            error(String.format("No se pudo mover “%s” a la papelera", Vault.title(p)), reason(e));
        }
    }

    /** Another solution at the cursor, starting with the C++ template: "Brute force", "Optimal"… */
    private void newSolution() {
        setReading(false);
        String code = current.get("judge").equals("LeetCode") ? "" : template(notebook);
        editor.insert("\n" + "### Solución · O( )" + "\n\n```cpp\n" + code + "\n```\n");
        editor.focus();
    }

    /** Picks a snippet of the Code Library and puts its code at the cursor. */
    private void insertSnippet() {
        List<Entry> snippets = entries.stream()
                .filter(e -> type.kindOf(e.note()).id().equals("snippet") && !e.path().equals(note)).toList();
        if (snippets.isEmpty()) {
            error("La biblioteca de código está vacía", "Agrega un fragmento con + Nuevo ▾ → Fragmento, con su código en un bloque de código.");
            return;
        }
        // What a snippet is filed under (algorithms and techniques, or topics): shown and searched with its title.
        Kind kind = type.kinds().stream().filter(k -> k.id().equals("snippet")).findFirst().orElseThrow();
        Function<Entry, String> filed = e -> kind.fields().stream().filter(f -> f.input() == NotebookType.Input.LIST)
                .flatMap(f -> e.note().list(f.key()).stream()).collect(Collectors.joining(", "));
        TextField filter = new TextField();
        filter.setPromptText("Filtrar");
        ListView<Entry> list = new ListView<>();
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Entry e, boolean empty) {
                super.updateItem(e, empty);
                String under = e == null ? "" : filed.apply(e);
                setText(empty || e == null ? null : under.isEmpty() ? e.title() : e.title() + "  ·  " + under);
            }
        });
        Runnable narrow = () -> {
            String wanted = NotebookView.fold(filter.getText().strip());
            list.getItems().setAll(snippets.stream().filter(e -> NotebookView.fold(e.title() + " " + filed.apply(e)).contains(wanted)).toList());
            list.getSelectionModel().selectFirst();
        };
        filter.textProperty().addListener((o, was, now) -> narrow.run());
        narrow.run();
        pick("Insertar fragmento", "Insertar", filter, list).ifPresent(e -> insertSnippet(e.path()));
    }

    /** Ctrl+O: any note of any notebook by a part of its title; with nothing typed, the latest first. */
    void quickOpen() {
        List<Vault.Place> all = vault.everyNote();
        Map<Path, FileTime> times = new HashMap<>();
        all.forEach(p -> times.put(p.note(), modified(p.note())));
        TextField title = new TextField();
        title.setPromptText("Título");
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
        pick("Abrir rápido", "Abrir", title, list).ifPresent(this::go);
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
        words.setPromptText("Palabras a buscar");
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
        pick("Buscar en todas las notas", "Abrir", words, list).ifPresent(hit -> {
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
                error(String.format("No se pudo crear “%s”", title), reason(e));
            }
        }
    }

    /** The titles [[ offers while writing, from every notebook. */
    private void refreshTitles() {
        editor.setTitles(vault.everyNote().stream().map(Vault.Place::title).distinct().sorted(Vault::compareNatural).toList());
    }

    /**
     * A paste in which the editor found no text (it pastes text itself): an image (a screenshot,
     * "Copy image") or image files go to attachments/ and into the note. Text is pasted here too, for
     * when the clipboard was busy (another program reading it) as the editor looked.
     */
    private void paste() {
        Clipboard clip = Clipboard.getSystemClipboard();
        if (note == null || reading) return;
        try {
            if (clip.hasString()) {   // also text copied with a picture of it, as from a spreadsheet
                editor.insert(clip.getString());
            } else if (clip.hasFiles() && clip.getFiles().stream().anyMatch(f -> Vault.isImage(f.toPath()))) {
                attachImages(clip.getFiles());
            } else if (clip.hasImage()) {
                editor.insert(imageLink(Vault.attach(notebook, png(clip.getImage()))));
            }
        } catch (IOException e) {
            error("No se pudo pegar la imagen", reason(e));
        }
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
            error("Nada que insertar", String.format("“%s” aún no tiene un bloque de código.", Vault.title(snippet)));
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
        ContextMenu menu = new ContextMenu(item("Cambiar nombre…", () -> renameNote(p)));
        Note n = entries.stream().filter(e -> e.path().equals(p)).map(Entry::note).findFirst().orElse(null);
        if (n != null && type.course() && type.kindOf(n).id().equals("assignment")) {
            boolean done = n.get("status").equalsIgnoreCase("Done");
            menu.getItems().add(item(done ? "Marcar como pendiente" : "Marcar como hecha", () -> setProperty(p, "status", done ? "Pending" : "Done")));
        }
        menu.getItems().addAll(new SeparatorMenuItem(), danger(item("Mover a la papelera…", () -> trashNote(p))));
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
            error(String.format("No se pudo cambiar “%s”", Vault.title(p)), reason(e));
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
        editTab.setSelected(!on);
        readTab.setSelected(on);
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
            status.setText(String.format("Sin guardar: %s", saveError));   // tried again on the next change or when leaving
            return false;
        }
    }

    /** Saves the open note before leaving it; if that fails, asks whether to leave it anyway. */
    private boolean leaveNote() {
        return save() || confirm("¿Salir sin guardar?", String.format("Los últimos cambios de “%s” no se pudieron guardar (%s). Si sales, se pierden.",
                Vault.title(note), saveError), "Salir de todos modos");
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
            error("No se pudo abrir la carpeta", dir.toString());
        }
    }

    /** A short reason people understand; the exception's own message is often just a path. */
    private static String reason(IOException e) {
        if (e instanceof FileAlreadyExistsException) return "ya hay uno con ese nombre";
        if (e instanceof AccessDeniedException) return "se negó el acceso al archivo";
        if (e instanceof NoSuchFileException) return "el archivo ya no está";
        if (e instanceof CharacterCodingException) return "no es texto UTF-8";
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private static String count(int notes) {
        return notes == 0 ? "sin notas" : notes == 1 ? "1 nota" : String.format("%d notas", notes);
    }

    private static Button button(String text, String icon, String style, EventHandler<ActionEvent> action) {
        Button b = button(text, style, action);
        b.setGraphic(Icons.of(icon, 16));
        return b;
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

    /** The menu of what else can be done here, as a button that says so. */
    private static MenuButton options(MenuItem... items) {
        MenuButton options = new MenuButton("Opciones", Icons.of("options", 16), items);
        options.getStyleClass().add("flat");
        return options;
    }

    /** Two or more toggles joined into one control. */
    private static HBox segmented(ToggleButton... buttons) {
        ToggleGroup group = new ToggleGroup();
        for (ToggleButton b : buttons) b.setToggleGroup(group);
        buttons[0].getStyleClass().add("first");
        buttons[buttons.length - 1].getStyleClass().add("last");
        HBox box = new HBox(buttons);
        box.getStyleClass().add("segmented");
        return box;
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
        link.setPromptText("https://codeforces.com/…, atcoder.jp/…, leetcode.com/… (opcional)");
        link.setPrefColumnCount(36);
        title.setPromptText("Título");
        String[] suggested = {""};
        link.textProperty().addListener((o, was, now) -> {
            if (!title.getText().isBlank() && !title.getText().equals(suggested[0])) return;   // typed by hand: keep it
            suggested[0] = Judges.fromLink(now).map(Judges.Problem::title).orElse("");
            title.setText(suggested[0]);
        });
        Label linkName = new Label("Enlace"), titleName = new Label("Título");
        linkName.getStyleClass().add("property-name");
        titleName.getStyleClass().add("property-name");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Nuevo problema");
        dialog.setHeaderText("Nuevo problema");
        style(dialog);
        ButtonType create = new ButtonType("Crear", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(create, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(6, linkName, link, titleName, title));
        dialog.getDialogPane().lookupButton(create).disableProperty().bind(title.textProperty().map(String::isBlank));
        Platform.runLater(link::requestFocus);
        return dialog.showAndWait().filter(b -> b == create).map(b -> new String[]{title.getText().strip(), link.getText().strip()});
    }

    private record NotebookChoice(String name, NotebookType type, Period period) {
    }

    /** Asks for a notebook's type, and for its name when {@code withName}. */
    private Optional<NotebookChoice> askNotebook(String title, NotebookType selected, boolean withName) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        style(dialog);
        TextField name = new TextField();
        name.setPromptText("Nombre");
        name.setPrefColumnCount(28);
        VBox content = new VBox(12);
        if (withName) content.getChildren().add(name);
        ToggleGroup types = new ToggleGroup();
        for (NotebookType each : NotebookType.ALL) {
            RadioButton option = new RadioButton(each.name());
            option.setUserData(each);
            option.setToggleGroup(types);
            option.setSelected(each.equals(selected));
            Label about = new Label(each.description());
            about.getStyleClass().add("type-description");
            content.getChildren().add(new VBox(2, option, about));
        }
        // A course belongs to a school period; the other notebooks show under the period they were last edited in.
        ComboBox<Period> period = periodChoice(Period.of(LocalDate.now()));
        Label periodName = new Label("Periodo");
        periodName.getStyleClass().add("property-name");
        HBox periodRow = new HBox(10, periodName, period);
        periodRow.setAlignment(Pos.CENTER_LEFT);
        if (withName) {
            content.getChildren().add(periodRow);
            Runnable showPeriod = () -> {
                boolean course = ((NotebookType) types.getSelectedToggle().getUserData()).course();
                periodRow.setVisible(course);
                periodRow.setManaged(course);
                if (dialog.getDialogPane().getScene() != null) dialog.getDialogPane().getScene().getWindow().sizeToScene();
            };
            types.selectedToggleProperty().addListener((o, was, now) -> showPeriod.run());
            showPeriod.run();
        }
        ButtonType ok = new ButtonType(withName ? "Crear" : "Cambiar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        if (withName) {
            dialog.getDialogPane().lookupButton(ok).disableProperty().bind(name.textProperty().map(String::isBlank));
            Platform.runLater(name::requestFocus);
        }
        return dialog.showAndWait().filter(b -> b == ok)
                .map(b -> {
                    NotebookType chosen = (NotebookType) types.getSelectedToggle().getUserData();
                    return new NotebookChoice(name.getText().strip(), chosen, withName && chosen.course() ? period.getValue() : null);
                });
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
