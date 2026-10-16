package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Badge;
import com.devavaxp.notes.NotebookType.Kind;
import com.devavaxp.notes.NotebookView.Entry;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
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
import javafx.util.StringConverter;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
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
    private static final KeyCombination PRINT = new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN);

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
    /** Bold, lists, links, formulas and code blocks with a click, above the note while it is written. */
    private final HBox formatBar = new HBox(2);
    /** In the bar, once GitHub has a newer version: a button to its page, to download it and install it over this one. */
    private final Button update = button("", "download", "update", e -> { });
    private final List<Button> formatButtons = new ArrayList<>();
    private Button blockButton;
    /** The notebook's language for new code blocks, in its bar; a click changes it. */
    private final Button languageChip = new Button();
    /** Editar or Vista previa: one is always pressed, and Ctrl+E switches. */
    private final ToggleButton editTab = new ToggleButton("Editar"), readTab = new ToggleButton("Vista previa");
    /** The printer, in the note's header: Exportar a PDF and Imprimir (Ctrl+P). */
    private final MenuButton paper = new MenuButton(null, Icons.of("printer", 16));
    /** A note on its way to paper: from asking the page for it until Edge has made the PDF, or opened its print window. */
    private boolean paperBusy;
    /** Which way to paper is the one now, so that one given up on does not go on. */
    private int paperJob;
    /** How long the page may take to give a note back for paper. */
    private final PauseTransition paperPatience = new PauseTransition(Duration.seconds(20));
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

    /** This version, from the jar's manifest; "dev" when run from the code. */
    static final String VERSION = Objects.requireNonNullElse(NotesApp.class.getPackage().getImplementationVersion(), "dev");

    public static void main(String[] args) {
        System.setProperty("java.net.useSystemProxies", "true");   // the proxy Windows has (a school's network), as browsers do
        launch(args);
    }

    /** As the app ends: another launch may open at once. */
    @Override
    public void stop() {
        Instance.release();
    }

    @Override
    public void start(Stage stage) {
        Vault vault = new Vault(Vault.defaultRoot());
        // Already open on these notes: that window comes to the front instead of a second one.
        if (!Instance.claim(Instance.local(), vault.root, () -> Platform.runLater(() -> toFront(stage)))) {
            Platform.exit();
            return;
        }
        ErrorLog.start(Instance.local().resolve(ErrorLog.NAME), VERSION);
        show(stage, vault);
        Updates.check(VERSION, release -> Platform.runLater(() -> showUpdate(release)));
    }

    /** A newer version on GitHub: its button in the bar, "Nueva versión 2.2.0", to its page. */
    void showUpdate(Updates.Release release) {
        update.setText("Nueva versión " + release.version());
        update.setTooltip(new Tooltip("Hay una versión nueva de Devava Notes: descárgala e instálala sobre esta; tus notas se quedan"));
        update.setOnAction(e -> openLink(release.page()));
        update.setVisible(true);
        update.setManaged(true);
    }

    /** The window, in front of the others; Windows brings forward one that was behind only while it is "always on top". */
    private static void toFront(Stage stage) {
        stage.setIconified(false);
        stage.setAlwaysOnTop(true);
        stage.setAlwaysOnTop(false);
        stage.toFront();
        stage.requestFocus();
    }

    void show(Stage stage, Vault vault) {
        Locale.setDefault(Text.SPANISH);   // the stock dialogs' "Cancelar" and the calendars too
        this.stage = stage;
        this.vault = vault;
        update.setVisible(false);
        update.setManaged(false);
        editor = new Editor(this::edited, this::openLink, this::openTitle, tag -> browser.showTag(tag), this::paste,
                this::languageChosen, this::editDrawing);
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
        paper.getItems().setAll(item("Exportar a PDF…", () -> exportPdf(note)), item("Imprimir…", () -> print(note)));
        paper.getStyleClass().add("flat");
        paper.setMinWidth(Region.USE_PREF_SIZE);
        paper.setAccessibleText("Exportar a PDF o imprimir");
        paper.setTooltip(new Tooltip("Exportar a PDF o imprimir la nota, como se ve en Vista previa (Ctrl+P imprime)"));
        actions.getChildren().setAll(status, tools, paper, modes);
        actions.setAlignment(Pos.CENTER_RIGHT);
        noteHeader.getChildren().setAll(noteTitle, kindName, badge, grow(), actions);
        noteHeader.getStyleClass().add("note-header");
        noteHeader.setAlignment(Pos.CENTER_LEFT);
        noteHeader.widthProperty().addListener((o, was, width) -> fitHeader());
        buildFormatBar();
        languageChip.getStyleClass().addAll("flat", "language-chip");
        languageChip.setMinWidth(Region.USE_PREF_SIZE);
        languageChip.setGraphic(Icons.of("code", 14));
        languageChip.setTooltip(new Tooltip("El lenguaje de los bloques de código nuevos; clic para cambiarlo"));
        languageChip.setOnAction(e -> chooseLanguage(notebook));
        actionsRow.getStyleClass().add("note-actions");
        actionsRow.setAlignment(Pos.CENTER_RIGHT);
        actionsRow.setManaged(false);
        actionsRow.setVisible(false);
        notebookView = new SplitPane(browser.sidebar, browser.notes, notePane);
        notebookView.setDividerPositions(0.19, 0.45);
        browser.tableMode().addListener((o, was, table) -> notebookView.setDividerPositions(0.19, table ? 0.68 : 0.45));
        SplitPane.setResizableWithParent(browser.sidebar, false);
        notePane.setMinWidth(400);
        ((Region) browser.sidebar).setMinWidth(236);   // "Sesiones de código" and "Biblioteca de código", with their counts
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
            } else if (PRINT.match(e) && note != null) {
                print(note);
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
        Button add = button("Nueva materia", "plus", "primary", e -> newNotebook());
        add.setTooltip(new Tooltip("Ctrl+N"));
        bar.getChildren().setAll(brand(), grow(), update, finder(), add, help(), options(
                item("Buscar en todas las notas…", this::search),
                item("Importar un cuaderno (ZIP)…", this::importNotebook),
                item("Abrir la carpeta de datos", () -> showInFolder(vault.root)),
                item("Reportar un problema…", this::reportProblem),
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
                {"Ctrl+N", "Nueva materia en Inicio; nueva nota dentro de una materia o un cuaderno"},
                {"Ctrl+O", "Abrir cualquier nota por su título"},
                {"Ctrl+Shift+F", "Buscar en el texto de todas las notas"},
                {"Ctrl+E", "Pasar entre Editar y Vista previa"},
                {"Ctrl+P", "Imprimir la nota; el botón de la impresora también la exporta a PDF"},
                {"Ctrl+F", "Buscar y reemplazar dentro de la nota"},
                {"Ctrl+B, Ctrl+I", "Negrita y cursiva; la barra sobre la nota tiene lo demás"},
                {"```java", "Un bloque de código al escribir ``` y su lenguaje, y luego Enter"},
                {"Ctrl+V", "Pegar texto, una imagen como una captura de pantalla, o celdas de Excel como tabla"},
                {"Tab, Enter", "En una tabla: la celda siguiente y la de abajo, que agrega filas al final"},
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
        Label version = new Label("Devava Notes " + VERSION + " · devava XP Studios");
        version.getStyleClass().add("hint");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ayuda");
        dialog.setHeaderText("Atajos de teclado");
        style(dialog);
        ButtonType report = new ButtonType("Reportar un problema…", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().setAll(report, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(new VBox(18, grid, where, version));
        if (dialog.showAndWait().orElse(ButtonType.CLOSE) == report) reportProblem();
    }

    /**
     * Reportar un problema: GitHub's form for a new issue, filled in with this version and the last
     * lines of the error log, to read (and change) before sending; or the whole log, to attach it.
     */
    void reportProblem() {
        Label about = new Label("Se abre GitHub con una plantilla para contar qué pasó, con la versión de la app y las últimas líneas de su"
                + " registro de errores. Revisa el texto antes de publicarlo: cualquiera puede verlo. Hace falta una cuenta de GitHub, que es"
                + " gratis. Con «Ver el registro» puedes adjuntarlo completo.");
        about.setWrapText(true);
        about.setPrefWidth(460);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reportar un problema");
        dialog.setHeaderText("Reportar un problema");
        style(dialog);
        ButtonType open = new ButtonType("Abrir GitHub", ButtonBar.ButtonData.OK_DONE), log = new ButtonType("Ver el registro", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().setAll(log, open, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(about);
        ButtonType chosen = dialog.showAndWait().orElse(ButtonType.CANCEL);
        Path file = ErrorLog.file();
        if (chosen == open) {
            openLink(issueLink(VERSION, file == null ? "" : ErrorLog.tail(file, 1500)));
        } else if (chosen == log) {
            if (file == null || !Files.exists(file)) {
                error("Aún no hay registro", "No se ha registrado ningún error.");
                return;
            }
            openFile(file);   // in the Notepad, say
        }
    }

    /** How long a link may be for Windows to pass it whole to the browser (rundll32, which JavaFX opens links with). */
    static final int LONGEST_LINK = 2000;

    /**
     * The link to a new issue of the app on GitHub, its text a template with the version, the system
     * and the last lines of the log that fit in such a link.
     */
    static String issueLink(String version, String log) {
        while (true) {
            String body = "**Qué pasó**\n\n\n**Qué esperabas**\n\n\n**Cómo repetirlo**\n1. \n\n---\nDevava Notes " + version + " · "
                    + System.getProperty("os.name") + " " + System.getProperty("os.version") + " · Java " + System.getProperty("java.version")
                    + (log.isEmpty() ? "" : "\n\n<details><summary>Registro de errores</summary>\n\n```\n" + log + "\n```\n</details>");
            String link = "https://github.com/HideOnVava/devava-notes/issues/new?body=" + URLEncoder.encode(body, StandardCharsets.UTF_8).replace("+", "%20");
            if (link.length() <= LONGEST_LINK || log.isEmpty()) return link;
            log = log.indexOf('\n') < 0 ? "" : log.substring(log.indexOf('\n') + 1);   // the oldest line goes
        }
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
        if (notebooks.isEmpty()) return firstUse();
        // On the left the subjects by school period, the newest first, then the free notebooks, which
        // have no period, and the earlier periods folded away; on the right what is coming and what
        // was written last.
        Period now = Period.of(LocalDate.now());
        Map<Period, List<Vault.Notebook>> byPeriod = new TreeMap<>(Comparator.reverseOrder());
        List<Vault.Notebook> free = new ArrayList<>();
        for (Vault.Notebook nb : notebooks) {
            if (nb.type().course()) byPeriod.computeIfAbsent(nb.period(), k -> new ArrayList<>()).add(nb);
            else free.add(nb);
        }
        VBox main = new VBox(30), older = new VBox(30);
        int olderCount = 0;
        for (Map.Entry<Period, List<Vault.Notebook>> group : byPeriod.entrySet()) {
            boolean past = group.getKey().compareTo(now) < 0;
            (past ? older : main).getChildren().add(section(group.getKey().toString(), countNotebooks(group.getValue().size()), group.getValue()));
            if (past) olderCount += group.getValue().size();
        }
        if (main.getChildren().isEmpty()) {
            VBox none = placeholder("Aún no tienes materias de " + now, "Agrega las que llevas este semestre, desde la retícula.");
            none.getChildren().add(button("Nueva materia", "plus", "primary", e -> newNotebook()));
            main.getChildren().add(none);
        }
        main.getChildren().add(freeSection(free));
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

    /** A section of Home, a school period or the free notebooks: its name, how many notebooks it has, and their cards. */
    private VBox section(String title, String count, List<Vault.Notebook> notebooks, Node... end) {
        Label name = new Label(title), counted = new Label(count);
        name.getStyleClass().add("period-title");
        counted.getStyleClass().add("period-count");
        HBox head = new HBox(10, name, counted);
        head.getChildren().addAll(end);
        head.setAlignment(Pos.BASELINE_LEFT);
        FlowPane cards = new FlowPane(14, 14);
        cards.setRowValignment(VPos.TOP);
        cards.getChildren().setAll(notebooks.stream().map(this::card).toList());
        return new VBox(12, head, cards);
    }

    /** The notebooks that are no subject (Libre, Programación competitiva), and the button to make one; it says what they are for while there are none. */
    private Node freeSection(List<Vault.Notebook> notebooks) {
        int n = notebooks.size();
        Button add = button("Nuevo cuaderno libre", "plus", "flat", e -> newFreeNotebook());
        VBox section = section("Cuadernos libres", n == 0 ? "" : n == 1 ? "1 cuaderno" : n + " cuadernos", notebooks, grow(), add);
        if (n == 0) {
            Label about = new Label("Para lo que no es de una materia: ideas, proyectos, la residencia…");
            about.getStyleClass().add("hint");
            section.getChildren().set(1, about);
        }
        return section;
    }

    private static String countNotebooks(int n) {
        return n == 1 ? "1 materia" : n + " materias";
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
            case "programming" -> "#2B4FD8";
            case "competitive-programming" -> "#D9590B";
            case "databases" -> "#0377B5";
            default -> "#5D6B82";
        };
    }

    /** Each type's icon. */
    private static String icon(NotebookType type) {
        return switch (type.id()) {
            case "class-notes" -> "book";
            case "programming" -> "code";
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
        when.setMinWidth(112);
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
        if (day.equals(LocalDate.now().minusDays(1))) return "Ayer";
        return DateTimeFormatter.ofPattern(day.getYear() == LocalDate.now().getYear() ? "d MMM" : "d MMM yyyy", Text.SPANISH).format(day);
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
        Label kind = new Label(kindLabel(nb.type(), codeLanguage(nb.dir())));
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
                item("Cambiar tipo…", () -> changeType(dir))));
        if (Vault.type(dir).course()) items.add(item("Cambiar periodo…", () -> changePeriod(dir)));   // a free notebook has none
        items.add(item("Lenguaje del código…", () -> chooseLanguage(dir)));
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
        Label about = new Label("En Inicio, aparece junto con las demás materias de ese semestre.");
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

    /** The language of a notebook's new code blocks: its own, else its type's (C++, PL/SQL), else none. */
    private static String codeLanguage(Path dir) {
        String own = Vault.meta(dir, "language");
        if (!own.isEmpty()) return own;
        NotebookType type = Vault.type(dir);
        return type.equals(NotebookType.COMPETITIVE_PROGRAMMING) ? "cpp" : type.equals(NotebookType.DATABASES) ? "plsql" : "";
    }

    /** The open notebook's language in its bar, when it has one. */
    private void showLanguage() {
        String id = notebook == null ? "" : codeLanguage(notebook);
        languageChip.setText(Editor.shortName(id));
        languageChip.setVisible(!id.isEmpty());
        languageChip.setManaged(!id.isEmpty());
    }

    /** The language a notebook's new code blocks start with; each block can still change its own. */
    private void chooseLanguage(Path dir) {
        ComboBox<String> languages = languageChoice(codeLanguage(dir));
        Label about = new Label("Los bloques de código nuevos empiezan en este lenguaje; cada bloque puede cambiar el suyo.");
        about.getStyleClass().add("hint");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Lenguaje del código");
        dialog.setHeaderText("Lenguaje del código de “" + dir.getFileName() + "”");
        style(dialog);
        ButtonType ok = new ButtonType("Cambiar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(12, languages, about));
        if (dialog.showAndWait().filter(b -> b == ok).isEmpty()) return;
        try {
            Vault.setMeta(dir, "language", languages.getValue());
        } catch (IOException e) {
            error("No se pudo cambiar el lenguaje", reason(e));
            return;
        }
        if (dir.equals(notebook)) {
            editor.setCodeLanguage(languages.getValue());
            showLanguage();
        }
    }

    /** The languages to choose from, the first ones of the blocks' menu; {@code selected} among them ("" picks Java). */
    private static ComboBox<String> languageChoice(String selected) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().setAll(Editor.LANGUAGES.keySet());
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(String id) {
                return id == null ? "" : Editor.languageName(id);
            }

            @Override
            public String fromString(String name) {
                return name;
            }
        });
        selectLanguage(box, selected);
        return box;
    }

    private static void selectLanguage(ComboBox<String> box, String id) {
        if (!id.isEmpty() && !box.getItems().contains(id)) box.getItems().add(id);
        box.setValue(id.isEmpty() ? "java" : id);
    }

    /** A type's name, with the language of its code when it has one: "Programación · Java". */
    private static String kindLabel(NotebookType type, String language) {
        boolean shown = !language.isEmpty() && type.code() && type != NotebookType.COMPETITIVE_PROGRAMMING;
        return type.name() + (shown ? " · " + Editor.shortName(language) : "");
    }

    /** The first language chosen in a block of a notebook without one becomes the notebook's, for its next blocks. */
    private void languageChosen(String id) {
        if (notebook == null || !codeLanguage(notebook).isEmpty()) return;
        try {
            Vault.setMeta(notebook, "language", id);
        } catch (IOException e) {
            return;   // the block keeps its language; the next ones ask again
        }
        editor.setCodeLanguage(id);
        showLanguage();
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
            error("No se pudo exportar", reason(e));
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
            error("No se pudo importar", reason(e));
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
        create(askSubject(), "No se pudo crear la materia");
    }

    /** Nuevo cuaderno libre: a notebook that is no subject, so it has no period. */
    void newFreeNotebook() {
        create(askFree(), "No se pudo crear el cuaderno");
    }

    private void create(Optional<NotebookChoice> asked, String failure) {
        asked.ifPresent(choice -> {
            try {
                openNotebook(createNotebook(choice));
            } catch (IOException e) {
                error(failure, reason(e));
            }
        });
    }

    /** A new notebook as asked: its type, and its period, language and subject when it has them. */
    private Path createNotebook(NotebookChoice choice) throws IOException {
        Path dir = createNotebook(choice.name(), choice.type());
        if (choice.period() != null) Vault.setMeta(dir, "period", choice.period().toString());
        if (!choice.language().isEmpty()) Vault.setMeta(dir, "language", choice.language());
        if (choice.subject() != null) Vault.setMeta(dir, "subject", choice.subject());
        return dir;
    }

    Path createNotebook(String name, NotebookType type) throws IOException {
        return vault.createNotebook(name, type);
    }

    private void renameNotebook(Path dir) {
        ask("Cambiar nombre", "Nombre", dir.getFileName().toString()).ifPresent(name -> {
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
                error("No se pudo cambiar el nombre", reason(e));
            }
        });
    }

    private void changeType(Path dir) {
        askType(String.format("Cambiar “%s” a", dir.getFileName()), Vault.type(dir)).ifPresent(chosen -> {
            boolean open = dir.equals(notebook);
            if (open && !leaveNote()) return;
            try {
                Vault.setType(dir, chosen);
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
        if (!confirm(String.format("¿Mover “%s” a la papelera?", name), notes == 0 ? "Va a la papelera, de donde se puede restaurar."
                : String.format("Va a la papelera con sus %s, de donde se puede restaurar todo.", count(notes)), "Mover a la papelera")) return;
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
        typeName.setMinWidth(Region.USE_PREF_SIZE);
        NotebookView.colored(typeName, color(type));
        List<MenuItem> menu = new ArrayList<>(List.of(notebookItems(dir)));
        menu.addAll(List.of(new SeparatorMenuItem(), themeItem()));
        HBox title = new HBox(10, tile(type, 26), name, typeName, languageChip);
        title.setAlignment(Pos.CENTER_LEFT);
        editor.setCodeLanguage(codeLanguage(dir));
        showLanguage();
        bar.getChildren().setAll(button("Inicio", "back", "flat", e -> showHome()), title, grow(), update, finder(), newButton(), help(),
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
        add.setMinWidth(Region.USE_PREF_SIZE);
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
        if (type.kinds().stream().anyMatch(k -> k.id().equals("snippet"))) {
            tools.getChildren().add(button("Insertar fragmento…", "snippet", "flat", e -> insertSnippet()));
        }
        properties.show(kind, n);
        editor.open(n.body, p.getParent());
        notePane.setTop(new VBox(noteHeader, actionsRow, properties.node, formatBar));
        showFormatBar();
        notePane.setCenter(editor.view);
        browser.select(p);
        fitHeader();
    }

    /** The formatting bar: what Markdown would need typing, a click away; code blocks in the notebook's language. */
    private void buildFormatBar() {
        Button block = formatButton("Bloque de código", Icons.of("code", 15), "Agrega un bloque de código, en el lenguaje de la materia",
                editor::newBlock);
        block.getStyleClass().add("format-code");
        blockButton = block;
        formatBar.getChildren().setAll(
                formatButton(null, glyph("B", "bold"), "Negrita (Ctrl+B)", () -> editor.format("bold")),
                formatButton(null, glyph("I", "italic"), "Cursiva (Ctrl+I)", () -> editor.format("italic")),
                formatButton("Título", Icons.of("heading", 15), "Título de una sección", () -> editor.format("heading")),
                formatSeparator(),
                formatButton("Lista", Icons.of("list", 15), "Lista con viñetas", () -> editor.format("list")),
                formatButton("Casilla", Icons.of("task", 15), "Lista de pendientes, con casillas para marcar", () -> editor.format("check")),
                tableButton(),
                formatSeparator(),
                formatButton("Enlace", Icons.of("link", 15), "Enlace a otra nota: elige su título", () -> editor.format("link")),
                formatButton("Imagen", Icons.of("image", 15), "Agrega una imagen desde un archivo", this::chooseImages),
                formatButton("Dibujo", Icons.of("draw", 15), "Dibuja un diagrama o un esquema, y ponlo en la nota", this::newDrawing),
                formatButton("Fórmula", Icons.of("sigma", 15), "Fórmula matemática, como en LaTeX", () -> editor.format("math")),
                formatSeparator(),
                block);
        formatBar.getStyleClass().add("format-bar");
        formatBar.setAlignment(Pos.CENTER_LEFT);
        formatBar.widthProperty().addListener((o, was, width) -> fitFormatBar());
    }

    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");

    /** Tabla: a grid of squares, as Word's, to choose how many columns and rows a new table starts with. */
    private Button tableButton() {
        Button table = formatButton("Tabla", Icons.of("table", 15), "Tabla: elige cuántas columnas y filas", () -> { });
        Label size = new Label("Elige columnas y filas");
        size.getStyleClass().add("sizes-title");
        GridPane squares = new GridPane();
        squares.setHgap(3);
        squares.setVgap(3);
        ContextMenu menu = new ContextMenu(new CustomMenuItem(new VBox(8, size, squares), false));
        menu.getStyleClass().add("table-sizes");
        for (int r = 1; r <= 8; r++) {
            for (int c = 1; c <= 8; c++) {
                int columns = c, rows = r;
                Region square = new Region();
                square.getStyleClass().add("size-square");
                square.setOnMouseEntered(e -> {
                    size.setText(columns + (columns == 1 ? " columna × " : " columnas × ") + rows + (rows == 1 ? " fila" : " filas"));
                    squares.getChildren().forEach(s -> s.pseudoClassStateChanged(ON,
                            GridPane.getColumnIndex(s) < columns && GridPane.getRowIndex(s) < rows));
                });
                square.setOnMouseClicked(e -> {
                    menu.hide();
                    editor.insertTable(columns, rows);
                    editor.focus();
                });
                squares.add(square, c - 1, r - 1);
            }
        }
        table.setOnAction(e -> menu.show(table, Side.BOTTOM, 0, 4));
        return table;
    }

    private Button formatButton(String text, Node graphic, String tip, Runnable action) {
        Button b = new Button(text, graphic);
        b.getStyleClass().add("format");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> {
            action.run();
            editor.focus();
        });
        formatButtons.add(b);
        return b;
    }

    private static Label glyph(String letter, String style) {
        Label glyph = new Label(letter);
        glyph.getStyleClass().addAll("glyph", style);
        return glyph;
    }

    private static Separator formatSeparator() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("format-separator");
        return separator;
    }

    /** The formatting bar while writing, not in Vista previa. */
    private void showFormatBar() {
        formatBar.setVisible(!reading);
        formatBar.setManaged(!reading);
    }

    /**
     * When the bar does not fit, its buttons keep only their icons (and say what they do on hover):
     * the small ones first, "Bloque de código" only when even that is not enough.
     */
    private void fitFormatBar() {
        formatButtons.forEach(b -> b.setContentDisplay(ContentDisplay.LEFT));
        formatBar.applyCss();
        double room = formatBar.getWidth();
        if (room <= 0 || formatBar.prefWidth(-1) <= room) return;
        formatButtons.stream().filter(b -> b != blockButton).forEach(b -> b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));
        if (formatBar.prefWidth(-1) > room) blockButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    /** The drawing window open now, for the tests. */
    Drawing drawing;

    /** A new drawing, in the drawing window; once done, saved in attachments/ and put in the note at the cursor. */
    void newDrawing() {
        if (note == null) return;
        Path at = notebook;
        openDrawing("Dibujo nuevo", "Insertar en la nota", null, png -> {
            try {
                Path file = Vault.saveDrawing(at, png);
                if (!at.equals(notebook)) return;
                setReading(false);
                editor.insert(imageLink(file));
                editor.focus();
            } catch (IOException e) {
                error("No se pudo guardar el dibujo", reason(e));
            }
        });
    }

    /** A drawing of the open note (as it links it, or as the reading view shows it), to draw on it again. */
    private void editDrawing(String src) {
        if (note == null) return;
        Path file;
        try {
            String path = src.replaceFirst("\\?.*$", "");
            file = (path.startsWith("file:") ? Path.of(URI.create(path)) : note.getParent().resolve(path.replaceAll("^<|>$", ""))).normalize();
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!file.startsWith(notebook.normalize()) || !Vault.isDrawing(file) || !Files.isRegularFile(file)) return;   // only the notebook's own drawings
        byte[] png;
        try {
            png = Files.readAllBytes(file);
        } catch (IOException e) {
            error("No se pudo abrir el dibujo", reason(e));
            return;
        }
        openDrawing("Editar dibujo", "Guardar dibujo", png, edited -> {
            try {
                Vault.write(file, edited);
                if (reading) editor.setReading(true);   // the view, with the drawing as it is now
            } catch (IOException e) {
                error("No se pudo guardar el dibujo", reason(e));
            }
        });
    }

    /** The drawing window, with the library of shapes every drawing shares; its links open in the browser. */
    private void openDrawing(String title, String action, byte[] png, Consumer<byte[]> onDone) {
        drawing = new Drawing(stage, title, action, png, dark, vault.library(), this::openLink, onDone);
        drawing.stage.show();
    }

    /** Pictures from files, saved in attachments/ and put in the note at the cursor. */
    private void chooseImages() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Agregar una imagen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg"));
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null) return;
        try {
            attachImages(files);
        } catch (IOException e) {
            error("No se pudo agregar la imagen", reason(e));
        }
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
        Note start = Note.parse(kind.template().replace("{today}", LocalDate.now().toString())
                .replace("{language}", codeLanguage(notebook)));
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
            if (note != null && !save()) {   // the open note as it is now: its links may change too
                error("No se pudo cambiar el nombre de la nota", String.format("Los últimos cambios de “%s” no están guardados: %s",
                        Vault.title(note), saveError));
                return;
            }
            List<Vault.Place> before = vault.everyNote();
            try {
                Path renamed = Vault.rename(p, title);
                if (open) {
                    note = renamed;
                    noteTitle.setText(Vault.title(renamed));
                    fitHeader();
                }
                refreshTitles();   // first: a note shown again shows its links as they are now
                relink(before, p, renamed);
                loadEntries(true);
                browser.update();
                if (reading && note != null) editor.setReading(true);
            } catch (IOException e) {
                error("No se pudo cambiar el nombre de la nota", reason(e));
            }
        });
    }

    /**
     * After a rename, each [[link]] that led to the note leads to its new title: in every note whose
     * [[old title]] meant this one (Vault.linkTarget). Not where [[new title]] would lead to another
     * note, nor when the new title cannot be a link: those keep the old one, and the user is told.
     */
    private void relink(List<Vault.Place> before, Path old, Path renamed) {
        String from = Vault.title(old), to = Vault.title(renamed);
        if (from.equalsIgnoreCase(to)) return;   // links ignore letter case: they still lead to it
        List<Vault.Place> after = vault.everyNote();
        Map<Path, Path> meant = new HashMap<>(), will = new HashMap<>();   // by notebook: where [[from]] led, where [[to]] will lead
        List<String> failed = new ArrayList<>(), elsewhere = new ArrayList<>();
        boolean linkable = !to.contains("]]") && !to.endsWith("]");   // [[Tarea [1]]] reads as a link to "Tarea [1"
        boolean unlinkable = false;
        for (Vault.Place place : before) {
            Path dir = place.notebook();
            if (!old.equals(meant.computeIfAbsent(dir, d -> Vault.linkTarget(before, from, d)))) continue;
            Path file = place.note().equals(old) ? renamed : place.note();
            try {
                // Read loosely first: a note that is not UTF-8 matters only if it has such a link.
                String loose = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (Vault.relink(loose, from, to).equals(loose)) continue;
                if (!linkable) {
                    unlinkable = true;
                } else if (!renamed.equals(will.computeIfAbsent(dir, d -> Vault.linkTarget(after, to, d)))) {
                    elsewhere.add(Vault.title(file));
                } else {
                    String text = Vault.read(file), linked = Vault.relink(text, from, to);
                    Vault.write(file, linked);
                    if (file.equals(note)) display(note, Note.parse(linked), Files.getLastModifiedTime(note));
                }
            } catch (IOException e) {
                failed.add(Vault.title(file));
            }
        }
        List<String> why = new ArrayList<>();
        if (unlinkable) why.add(String.format("Un [[enlace]] no puede llevar a “%s”: un título con «]]», o que termina en «]», no cabe entre"
                + " los corchetes. Usa otro nombre.", to));
        if (!elsewhere.isEmpty()) why.add(String.format("En %s, [[%s]] llevaría a otra nota que se llama igual: ahí quedó [[%s]]. Cámbialo a mano"
                + " o usa otro nombre.", quoted(elsewhere), to, from));
        if (!failed.isEmpty()) why.add(String.format("No se pudieron cambiar en %s: cámbialos a [[%s]] a mano.", quoted(failed), to));
        if (!why.isEmpty()) error(String.format("Algunos enlaces siguen como [[%s]]", from), String.join("\n\n", why));
    }

    private static String quoted(List<String> titles) {
        return titles.stream().map(t -> "“" + t + "”").collect(Collectors.joining(", "));
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
            error("La biblioteca de código está vacía", "Agrega uno con Nueva nota → Fragmento, con su código en un bloque de código.");
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
        Path found = Vault.linkTarget(vault.everyNote(), title, notebook);
        if (found != null) {
            go(new Vault.Place(found.getParent(), found));
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

    /** The open notebook's name, as its views say it: "Aún no tienes apuntes en Cálculo Vectorial". */
    String notebookName() {
        return notebook == null ? "" : notebook.getFileName().toString();
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

    static byte[] png(Image image) throws IOException {
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

    /** The values a property already has in this notebook, for its ▾ menu; for topics, also its subject's (Reticula). */
    private List<String> usedValues(String key) {
        List<String> values = new ArrayList<>(entries.stream().flatMap(e -> e.note().list(key).stream()).distinct().toList());
        Reticula.Subject subject = notebook != null && (key.equals("topics") || key.equals("topic")) ? Reticula.of(notebook) : null;
        if (subject != null) subject.topics().stream().filter(t -> values.stream().noneMatch(t::equalsIgnoreCase)).forEach(values::add);
        return values;
    }

    ContextMenu noteMenu(Path p) {
        ContextMenu menu = new ContextMenu(item("Cambiar nombre…", () -> renameNote(p)));
        Note n = entries.stream().filter(e -> e.path().equals(p)).map(Entry::note).findFirst().orElse(null);
        if (n != null && type.course() && type.kindOf(n).id().equals("assignment")) {
            boolean done = n.get("status").equalsIgnoreCase("Done");
            menu.getItems().add(item(done ? "Marcar como pendiente" : "Marcar como hecha", () -> setProperty(p, "status", done ? "Pending" : "Done")));
        }
        menu.getItems().addAll(new SeparatorMenuItem(), item("Exportar a PDF…", () -> exportPdf(p)), item("Imprimir…", () -> print(p)),
                new SeparatorMenuItem(), danger(item("Mover a la papelera…", () -> trashNote(p))));
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
        showFormatBar();
    }

    // ------------------------------------------------------------------
    // Paper: a PDF, a print (Edge makes them; see Pdf)
    // ------------------------------------------------------------------

    /** Exportar a PDF…: where to save it, then the PDF of the note as Vista previa shows it, opened once made. */
    void exportPdf(Path p) {
        if (paperBusy) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar a PDF");
        chooser.setInitialFileName(Vault.title(p) + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File chosen = chooser.showSaveDialog(stage);
        if (chosen == null) return;
        Path pdf = chosen.toPath();
        if (!pdf.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) pdf = pdf.resolveSibling(pdf.getFileName() + ".pdf");
        exportPdf(p, pdf, this::openFile);
    }

    /** The PDF of a note as Vista previa shows it, at {@code pdf}; {@code done} gets it once made. */
    void exportPdf(Path p, Path pdf, Consumer<Path> done) {
        toPaper(p, false, "Creando PDF…", (edge, page) -> {
            Pdf.export(edge, page, pdf);
            return () -> done.accept(pdf);
        });
    }

    /** Imprimir… (Ctrl+P): Edge's print window, with a preview of the note as Vista previa shows it and the printer to choose. */
    void print(Path p) {
        toPaper(p, true, "Abriendo impresión…", (edge, page) -> {
            Pdf.print(edge, page);
            return () -> { };
        });
    }

    /** What Edge does with a note's page for paper, away from the window; and what follows, back on it. */
    private interface PaperJob {
        Runnable run(Path edge, String page) throws IOException;
    }

    /** A note, as it is now (in the editor, if open), as the page for paper that {@code job} gives Edge. */
    private void toPaper(Path p, boolean print, String doing, PaperJob job) {
        if (paperBusy || !editor.ready || notebook == null) return;
        String failed = print ? "No se pudo imprimir" : "No se pudo crear el PDF";
        Path edge = Pdf.edge(System::getenv);
        if (edge == null) {
            error(failed, "La app hace los PDF y las impresiones con Microsoft Edge, que viene con Windows, y aquí no está."
                    + " Instálalo desde microsoft.com/edge y vuelve a intentarlo.");
            return;
        }
        Note n;
        String body;
        if (p.equals(note)) {   // saved or not
            n = current;
            body = editor.text();
        } else {
            try {
                n = Note.parse(Vault.read(p));
            } catch (IOException e) {
                error(failed, reason(e));
                return;
            }
            body = n.body;
        }
        String title = Vault.title(p), about = about(n);
        int ticket = ++paperJob;
        busy(doing);
        paperPatience.setOnFinished(e -> {   // the page never gave the note back (it reloaded, say)
            paperJob++;
            editor.cancelPrintable();
            idle();
            error(failed, "La nota no terminó de prepararse. Vuelve a intentarlo.");
        });
        paperPatience.playFromStart();
        try {
            editor.printable(body, p.getParent(), html -> {
                if (ticket != paperJob) return;
                paperPatience.stop();
                Thread worker = new Thread(() -> {
                    Runnable then;
                    try {
                        then = job.run(edge, Pdf.page(title, about, html, print));
                    } catch (IOException | RuntimeException e) {
                        String why = e instanceof IOException io ? reason(io) : e.toString();
                        then = () -> error(failed, why);
                    }
                    Runnable after = then;
                    Platform.runLater(() -> {
                        idle();
                        after.run();
                    });
                }, "Devava Notes: paper");
                worker.setDaemon(true);
                worker.start();
            });
        } catch (RuntimeException e) {   // the page could not make it (a JSException)
            paperPatience.stop();
            paperJob++;
            idle();
            ErrorLog.add("printable: " + e);
            error(failed, "La nota no terminó de prepararse. Vuelve a intentarlo.");
        }
    }

    /** The printer button says what is going on, and waits, while a note goes to paper. */
    private void busy(String doing) {
        paperBusy = true;
        paper.setText(doing);
        paper.setDisable(true);
        root.setCursor(Cursor.WAIT);
        fitHeader();
    }

    private void idle() {
        paperBusy = false;
        paper.setText(null);
        paper.setDisable(false);
        root.setCursor(null);
        fitHeader();
    }

    /** What a note is, under its title on paper: "Estructura de Datos · Sesión de código · Unidad 3 · 15 de octubre de 2026". */
    String about(Note n) {
        List<String> parts = new ArrayList<>(List.of(notebook.getFileName().toString()));
        if (type.kinds().size() > 1) parts.add(type.kindOf(n).name());
        String unit = n.get("unit");
        if (!unit.isEmpty()) parts.add(Character.isDigit(unit.charAt(0)) ? "Unidad " + unit : unit);
        if (!n.get("date").isEmpty()) parts.add(longDate(n.get("date")));
        else if (!n.get("due").isEmpty()) parts.add("Entrega: " + longDate(n.get("due")));
        return String.join(" · ", parts);
    }

    /** 2026-10-15 as "15 de octubre de 2026"; anything else as it is. */
    static String longDate(String date) {
        try {
            return DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Text.SPANISH).format(LocalDate.parse(date));
        } catch (DateTimeParseException e) {
            return date;
        }
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
            ErrorLog.add("No se pudo guardar " + note + ": " + saveError);
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

    /** A file in the program Windows opens its kind with; with none, its folder. */
    private void openFile(Path file) {
        try {
            Desktop.getDesktop().open(file.toFile());
        } catch (IOException | RuntimeException e) {
            showInFolder(file.getParent());
        }
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
        if (e instanceof FileSystemException f && f.getReason() != null) return f.getReason();   // Windows' words, without the paths
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

    /** A button that keeps its words: when a bar is short of room, what gives way is a name, never "Ay…". */
    private static Button button(String text, String style, EventHandler<ActionEvent> action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setOnAction(action);
        b.setMinWidth(Region.USE_PREF_SIZE);
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
        options.setMinWidth(Region.USE_PREF_SIZE);
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

    static VBox placeholder(String title, String text) {
        Label t = new Label(title), s = new Label(text);
        t.getStyleClass().add("empty-title");
        s.getStyleClass().add("empty-text");
        t.setWrapText(true);
        s.setWrapText(true);
        VBox box = new VBox(8, t, s);
        box.getStyleClass().add("placeholder");
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(460);
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

    /** A new notebook: its name and type; its period and language when it has them; its subject when it is one of the curriculum. */
    private record NotebookChoice(String name, NotebookType type, Period period, String language, String subject) {
    }

    /** A name typed in "Nueva materia" that is no subject of the curriculum. */
    private record OtherName(String name) {
    }

    /**
     * The subjects of the curriculum that Nueva materia and the first use offer: Residencia
     * Profesional, Servicio Social and Actividades Complementarias are free notebooks instead.
     */
    private static final List<Reticula.Subject> SUBJECTS = Reticula.ISC.stream().filter(s -> s.type().course()).toList();

    /**
     * "Nueva materia": a subject of the curriculum, found by typing part of its name, whose type and
     * language the app proposes; or any other name. The type, the language and the period can change.
     */
    private Optional<NotebookChoice> askSubject() {
        TextField find = new TextField();
        find.setPromptText("Busca tu materia en la retícula, o escribe otro nombre");
        ListView<Object> results = new ListView<>();
        results.getStyleClass().add("subjects");
        results.setPrefHeight(260);
        results.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
                getStyleClass().remove("heading");
                if (empty || item == null) return;
                if (item instanceof String heading) {
                    setText(heading.toUpperCase(Text.SPANISH));
                    getStyleClass().add("heading");
                } else if (item instanceof Reticula.Subject s) {
                    setGraphic(subjectLine(tile(s.type(), 26), s.name(), kindLabel(s.type(), s.language()), color(s.type())));
                } else if (item instanceof OtherName o) {
                    setGraphic(subjectLine(tile(NotebookType.CLASS_NOTES, 26), "Crear “" + o.name() + "” con otro nombre", "", null));
                }
            }
        });

        ToggleGroup types = new ToggleGroup();
        HBox typeRow = new HBox(8);
        for (NotebookType t : NotebookType.ALL) {
            if (!t.course()) continue;   // the others are free notebooks
            Label name = new Label(t.name()), about = new Label(t.description());
            name.getStyleClass().add("option-name");
            name.setWrapText(true);
            NotebookView.colored(name, color(t));
            about.getStyleClass().add("option-about");
            about.setWrapText(true);
            ToggleButton option = new ToggleButton(null, new VBox(3, name, about));
            option.setWrapText(true);   // so its height is measured at its width
            option.getStyleClass().add("type-option");
            option.setUserData(t);
            option.setToggleGroup(types);
            option.setMaxHeight(Double.MAX_VALUE);
            option.setPrefWidth(184);   // three, as wide as the list above them
            typeRow.getChildren().add(option);
        }
        ComboBox<String> language = languageChoice("java");
        ComboBox<Period> period = periodChoice(Period.of(LocalDate.now()));
        HBox languageRow = labeled("Lenguaje", language), periodRow = labeled("Periodo", period);
        Runnable rows = () -> {
            NotebookType t = (NotebookType) types.getSelectedToggle().getUserData();
            languageRow.setVisible(t.code());
            languageRow.setManaged(t.code());
        };
        types.selectToggle(types.getToggles().get(0));
        // A subject proposes its type and language; another type, without one, starts with that type's language.
        boolean[] proposing = {false};
        types.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) {
                was.setSelected(true);
                return;
            }
            if (!proposing[0]) selectLanguage(language, now.getUserData() == NotebookType.DATABASES ? "plsql" : "java");
            rows.run();
        });
        results.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (now instanceof String) {   // a heading: the subject under it
                int at = results.getItems().indexOf(now);
                Platform.runLater(() -> results.getSelectionModel().select(Math.min(at + 1, results.getItems().size() - 1)));
            } else if (now instanceof Reticula.Subject s) {
                proposing[0] = true;
                types.getToggles().stream().filter(t -> t.getUserData() == s.type()).findFirst().ifPresent(types::selectToggle);
                proposing[0] = false;
                if (!s.language().isEmpty()) selectLanguage(language, s.language());
            }
        });
        Runnable filter = () -> {
            String typed = find.getText().strip(), wanted = NotebookView.fold(typed);
            List<Object> items = new ArrayList<>();
            int semester = 0;
            for (Reticula.Subject s : SUBJECTS) {
                if (!NotebookView.fold(s.name()).contains(wanted)) continue;
                if (s.semester() != semester) items.add(Reticula.semester(semester = s.semester()));
                items.add(s);
            }
            if (!typed.isEmpty() && SUBJECTS.stream().noneMatch(s -> NotebookView.fold(s.name()).equals(wanted))) {
                items.add("Otra");
                items.add(new OtherName(typed));
            }
            results.getItems().setAll(items);
            if (!typed.isEmpty()) items.stream().filter(i -> !(i instanceof String)).findFirst().ifPresent(results.getSelectionModel()::select);
        };
        find.textProperty().addListener((o, was, now) -> filter.run());
        filter.run();
        rows.run();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Nueva materia");
        dialog.setHeaderText("Nueva materia");
        style(dialog);
        ButtonType create = new ButtonType("Crear materia", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(create, ButtonType.CANCEL);
        Label typeName = new Label("Tipo");
        typeName.getStyleClass().add("property-name");
        dialog.getDialogPane().setContent(new VBox(12, find, results, typeName, typeRow, new HBox(22, languageRow, periodRow)));
        Node ok = dialog.getDialogPane().lookupButton(create);
        ok.disableProperty().bind(results.getSelectionModel().selectedItemProperty().map(i -> i instanceof String).orElse(true));
        // Down and Up go through the results from the field; Enter takes the one chosen.
        find.setOnKeyPressed(e -> {
            int at = results.getSelectionModel().getSelectedIndex(), step = e.getCode() == KeyCode.DOWN ? 1 : e.getCode() == KeyCode.UP ? -1 : 0;
            if (step == 0) return;
            for (int i = at + step; i >= 0 && i < results.getItems().size(); i += step) {
                if (results.getItems().get(i) instanceof String) continue;
                results.getSelectionModel().select(i);
                results.scrollTo(Math.max(0, i - 2));
                break;
            }
            e.consume();
        });
        results.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !ok.isDisabled()) ((Button) ok).fire();
        });
        Platform.runLater(find::requestFocus);
        return dialog.showAndWait().filter(b -> b == create).map(b -> {
            Object chosen = results.getSelectionModel().getSelectedItem();
            NotebookType t = (NotebookType) types.getSelectedToggle().getUserData();
            Reticula.Subject subject = chosen instanceof Reticula.Subject s ? s : null;
            String name = subject != null ? subject.name() : ((OtherName) chosen).name();
            return new NotebookChoice(name, t, period.getValue(), t.code() ? language.getValue() : "", subject == null ? null : subject.key());
        });
    }

    /** "Nuevo cuaderno libre": its name, and its type, of those that are no subject. */
    private Optional<NotebookChoice> askFree() {
        TextField name = new TextField();
        name.setPromptText("Ideas, Proyecto integrador, Concursos…");
        name.setPrefColumnCount(28);
        ToggleGroup types = new ToggleGroup();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Nuevo cuaderno libre");
        dialog.setHeaderText("Nuevo cuaderno libre");
        style(dialog);
        ButtonType create = new ButtonType("Crear cuaderno", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(create, ButtonType.CANCEL);
        List<NotebookType> free = List.of(NotebookType.GENERAL, NotebookType.COMPETITIVE_PROGRAMMING);
        dialog.getDialogPane().setContent(new VBox(16, name, typeChoice(types, free, NotebookType.GENERAL)));
        dialog.getDialogPane().lookupButton(create).disableProperty().bind(name.textProperty().map(String::isBlank));
        Platform.runLater(name::requestFocus);
        return dialog.showAndWait().filter(b -> b == create).map(b ->
                new NotebookChoice(name.getText().strip(), (NotebookType) types.getSelectedToggle().getUserData(), null, "", null));
    }

    /** A line of the subjects: its type's mark, its name, and what the app proposes for it. */
    private static Node subjectLine(Node tile, String name, String kind, String color) {
        Label title = new Label(name), proposal = new Label(kind);
        title.getStyleClass().add("subject-name");
        proposal.getStyleClass().add("subject-kind");
        if (color != null) NotebookView.colored(proposal, color);
        HBox line = new HBox(10, tile, title, grow(), proposal);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private static HBox labeled(String name, Node field) {
        Label label = new Label(name);
        label.getStyleClass().add("property-name");
        label.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(10, label, field);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * The first time, with no notebooks: the subjects of this semester, ticked in the curriculum,
     * each becomes a notebook with its type and language; and a note on how the app works.
     */
    private Node firstUse() {
        ImageView logo = new ImageView(icon);
        logo.setFitWidth(56);
        logo.setFitHeight(56);
        logo.setSmooth(true);
        Label title = new Label("Te damos la bienvenida a Devava Notes"), about = new Label(
                "¿Qué materias llevas este semestre? Elígelas de la retícula de ISC: cada una tendrá sus apuntes, tareas, exámenes y,"
                        + " en las de programación, sesiones de código.");
        title.getStyleClass().add("welcome-title");
        about.getStyleClass().add("welcome-text");
        about.setWrapText(true);
        TextField find = new TextField();
        find.setPromptText("Buscar una materia");
        find.setPrefColumnCount(24);
        ComboBox<Period> period = periodChoice(Period.of(LocalDate.now()));
        HBox top = new HBox(10, find, grow(), labeled("Periodo", period));
        top.setAlignment(Pos.CENTER_LEFT);

        Map<Reticula.Subject, CheckBox> boxes = new LinkedHashMap<>();
        Map<Integer, VBox> sections = new LinkedHashMap<>();
        VBox list = new VBox(14);
        list.getStyleClass().add("first-use-list");
        for (Reticula.Subject s : SUBJECTS) {
            VBox section = sections.computeIfAbsent(s.semester(), n -> {
                Label name = new Label(Reticula.semester(n).toUpperCase(Text.SPANISH));
                name.getStyleClass().add("section-title");
                Button all = button("Elegir todas", "flat", e -> {
                    List<CheckBox> those = boxes.entrySet().stream().filter(b -> b.getKey().semester() == n && b.getValue().isVisible())
                            .map(Map.Entry::getValue).toList();
                    boolean on = those.stream().anyMatch(b -> !b.isSelected());
                    those.forEach(b -> b.setSelected(on));
                });
                all.getStyleClass().add("small");
                HBox heading = new HBox(10, name, grow(), all);
                heading.setAlignment(Pos.CENTER_LEFT);
                VBox v = new VBox(2, heading);
                list.getChildren().add(v);
                return v;
            });
            CheckBox box = new CheckBox(s.name());
            Label kind = new Label(kindLabel(s.type(), s.language()));
            kind.getStyleClass().add("subject-kind");
            NotebookView.colored(kind, color(s.type()));
            HBox row = new HBox(10, box, grow(), kind);
            row.getStyleClass().add("subject-row");
            row.setAlignment(Pos.CENTER_LEFT);
            box.visibleProperty().bind(row.visibleProperty());
            boxes.put(s, box);
            section.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(320);
        scroll.getStyleClass().add("first-use-scroll");

        Button create = button("Crear materias", "plus", "primary", e -> startWith(boxes.entrySet().stream()
                .filter(b -> b.getValue().isSelected()).map(Map.Entry::getKey).toList(), period.getValue()));
        Button skip = button("Empezar sin materias", "flat", e -> startWith(List.of(), period.getValue()));
        Runnable count = () -> {
            long n = boxes.values().stream().filter(CheckBox::isSelected).count();
            create.setText(n == 0 ? "Crear materias" : n == 1 ? "Crear 1 materia" : "Crear " + n + " materias");
            create.setDisable(n == 0);
        };
        boxes.values().forEach(b -> b.selectedProperty().addListener((o, was, now) -> count.run()));
        count.run();
        find.textProperty().addListener((o, was, now) -> {
            String wanted = NotebookView.fold(now.strip());
            boxes.forEach((s, b) -> {
                Node row = b.getParent();
                boolean shown = NotebookView.fold(s.name()).contains(wanted);
                row.setVisible(shown);
                row.setManaged(shown);
            });
            sections.values().forEach(v -> {
                boolean any = v.getChildren().stream().skip(1).anyMatch(Node::isVisible);
                v.setVisible(any);
                v.setManaged(any);
            });
        });
        HBox actions = new HBox(10, create, skip);
        VBox panel = new VBox(14, logo, title, about, top, scroll, actions);
        panel.getStyleClass().add("first-use");
        panel.setMaxWidth(760);
        StackPane wrap = new StackPane(panel);
        wrap.setPadding(new Insets(28, 34, 28, 34));
        return wrap;
    }

    /** The first notebooks: the subjects ticked, and "Primeros pasos" with a note on how the app works. */
    private void startWith(List<Reticula.Subject> subjects, Period period) {
        try {
            for (Reticula.Subject s : subjects) createNotebook(new NotebookChoice(s.name(), s.type(), period, s.language(), s.key()));
            Path guide = createNotebook("Primeros pasos", NotebookType.GENERAL);
            try (var in = NotesApp.class.getResourceAsStream("guia.md")) {
                Vault.createNote(guide, "Cómo usar Devava Notes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            error("No se pudieron crear tus materias", reason(e));
        }
        showHome();
    }

    /** Asks for a notebook's type. */
    private Optional<NotebookType> askType(String title, NotebookType selected) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        style(dialog);
        ToggleGroup types = new ToggleGroup();
        ButtonType ok = new ButtonType("Cambiar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(typeChoice(types, NotebookType.ALL, selected));
        return dialog.showAndWait().filter(b -> b == ok).map(b -> (NotebookType) types.getSelectedToggle().getUserData());
    }

    /** Types to pick one of, each with what it is for. */
    private static VBox typeChoice(ToggleGroup group, List<NotebookType> types, NotebookType selected) {
        VBox content = new VBox(12);
        for (NotebookType each : types) {
            RadioButton option = new RadioButton(each.name());
            option.setUserData(each);
            option.setToggleGroup(group);
            option.setSelected(each.equals(selected));
            Label about = new Label(each.description());
            about.getStyleClass().add("type-description");
            content.getChildren().add(new VBox(2, option, about));
        }
        return content;
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
        ErrorLog.add(title + ": " + message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle(title);
        alert.setHeaderText(title);
        style(alert);
        alert.showAndWait();
    }
}
