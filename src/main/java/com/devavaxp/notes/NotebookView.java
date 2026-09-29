package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Badge;
import com.devavaxp.notes.NotebookType.Field;
import com.devavaxp.notes.NotebookType.Input;
import com.devavaxp.notes.NotebookType.View;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * How a notebook is browsed. On the left, the views of its type; a view that groups by a property
 * opens into that property's values. Next to it, the notes of the chosen view or value, as a list
 * or as a table, with a box to filter them.
 */
final class NotebookView {

    /** A note as the notebook lists it; the open note shares its Note, so its edits show at once. */
    record Entry(Path path, Note note, FileTime modified) {
        String title() {
            return Vault.title(path);
        }
    }

    /** The notes under one value of the view's grouping property ("": those without one; null: not grouped). */
    record Group(String value, List<Entry> notes) {
    }

    /** A line of the sidebar: a view, or one value of the property it groups by. */
    private record Choice(View view, String value, String label, int count) {
    }

    /** A line of the list or table: a group heading (with its label), or a note. */
    private record Row(Group group, String label, Entry entry) {
    }

    private static final PseudoClass GROUP = PseudoClass.getPseudoClass("group");

    final Node sidebar, notes;
    private final NotesApp app;
    private final TreeView<Choice> views = new TreeView<>(new TreeItem<>());
    private final TreeTableView<Row> table = new TreeTableView<>(new TreeItem<>());
    private final TextField filter = new TextField();
    private final ToggleButton asList = new ToggleButton("List"), asTable = new ToggleButton("Table");
    private final Set<String> expanded = new HashSet<>();
    private NotebookType type = NotebookType.GENERAL;
    private List<Entry> entries = List.of();
    private View view;
    private String value;
    private Path open;
    private boolean updating;   // while the lists are rebuilt, their selection events are not the user's

    NotebookView(NotesApp app) {
        this.app = app;
        views.setShowRoot(false);
        views.getStyleClass().add("views");
        views.setCellFactory(t -> new TreeCell<>() {
            @Override
            protected void updateItem(Choice c, boolean empty) {
                super.updateItem(c, empty);
                Label count = empty || c == null ? null : new Label(Integer.toString(c.count()));
                if (count != null) count.getStyleClass().add("count");
                setText(empty || c == null ? null : c.label());
                setGraphic(count);
                setContentDisplay(ContentDisplay.RIGHT);
            }
        });
        views.getSelectionModel().selectedItemProperty().addListener((o, old, item) -> {
            if (updating || item == null) return;
            view = item.getValue().view();
            value = item.getValue().value();
            showNotes();
        });

        table.setShowRoot(false);
        table.setPlaceholder(new Label("No notes here"));
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((o, old, item) -> {
            Entry e = item == null ? null : item.getValue().entry();
            if (!updating && e != null && !e.path().equals(open)) app.openNote(e.path());
        });
        table.setRowFactory(t -> new TreeTableRow<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                pseudoClassStateChanged(GROUP, !empty && row != null && row.entry() == null);
                setContextMenu(empty || row == null || row.entry() == null ? null : app.noteMenu(row.entry().path()));
            }
        });
        table.setOnKeyPressed(e -> {
            TreeItem<Row> item = table.getSelectionModel().getSelectedItem();
            Entry entry = item == null ? null : item.getValue().entry();
            if (entry == null) return;
            if (e.getCode() == KeyCode.ENTER) app.editor.focus();
            else if (e.getCode() == KeyCode.F2) app.renameNote(entry.path());
            else if (e.getCode() == KeyCode.DELETE) app.trashNote(entry.path());
        });

        filter.setPromptText("Filter");
        filter.textProperty().addListener((o, was, now) -> showNotes());
        ToggleGroup mode = new ToggleGroup();
        asList.setToggleGroup(mode);
        asTable.setToggleGroup(mode);
        asList.setSelected(true);
        asList.getStyleClass().add("first");
        asTable.getStyleClass().add("last");
        mode.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) was.setSelected(true);   // one of the two is always on
            else showNotes();
        });
        HBox segmented = new HBox(asList, asTable);
        segmented.getStyleClass().add("segmented");
        HBox tools = new HBox(8, filter, segmented);
        tools.getStyleClass().add("notes-tools");
        HBox.setHgrow(filter, Priority.ALWAYS);
        VBox box = new VBox(tools, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        sidebar = views;
        notes = box;
    }

    /** Shows another notebook, from its first view. */
    void open(NotebookType type, List<Entry> entries) {
        this.type = type;
        this.entries = entries;
        view = type.views().get(0);
        value = null;
        open = null;
        expanded.clear();
        filter.clear();
        update();
    }

    /** Shows the notes again after they changed, keeping the view and the open note. */
    void update() {
        updating = true;
        try {
            showViews();
        } finally {
            updating = false;
        }
        showNotes();
    }

    /** True while the notes show as a table (which wants more room than a list). */
    BooleanProperty tableMode() {
        return asTable.selectedProperty();
    }

    void showTable(boolean on) {
        (on ? asTable : asList).setSelected(true);
    }

    /** Shows a view by name, or one of its values. */
    void choose(String viewName, String value) {
        for (View v : type.views()) {
            if (v.name().equals(viewName)) {
                view = v;
                this.value = value;
                update();
            }
        }
    }

    /** Marks the note as the open one and selects its line. */
    void select(Path p) {
        open = p;
        TreeItem<Row> item = find(table.getRoot(), p);
        updating = true;
        try {
            if (item == null) {
                table.getSelectionModel().clearSelection();
            } else {
                table.getSelectionModel().select(item);
                table.scrollTo(table.getRow(item));
            }
        } finally {
            updating = false;
        }
    }

    /** Selects the note, first moving to a view that shows it if the current one does not. */
    void reveal(Path p) {
        open = p;
        if (find(table.getRoot(), p) == null) {
            for (View v : type.views()) {
                if (rows(type, v, null, "", entries).stream().anyMatch(g -> g.notes().stream().anyMatch(e -> e.path().equals(p)))) {
                    view = v;
                    value = null;
                    filter.clear();
                    update();
                    break;
                }
            }
        }
        select(p);
    }

    private void showViews() {
        List<TreeItem<Choice>> items = new ArrayList<>();
        TreeItem<Choice> chosen = null;
        for (View v : type.views()) {
            List<Group> groups = rows(type, v, null, "", entries);
            long count = groups.stream().flatMap(g -> g.notes().stream()).distinct().count();
            TreeItem<Choice> item = new TreeItem<>(new Choice(v, null, v.name(), (int) count));
            remember(item, v.name());
            if (v.groupBy() != null) {
                // Each value under its view, and "graphs/dijkstra" under "graphs".
                Map<String, TreeItem<Choice>> made = new HashMap<>();
                for (Map.Entry<String, Integer> c : counts(type, v, entries).entrySet()) {
                    String path = c.getKey();
                    int slash = path.lastIndexOf('/');
                    TreeItem<Choice> child = new TreeItem<>(new Choice(v, path, slash < 0 ? label(v, path) : path.substring(slash + 1), c.getValue()));
                    TreeItem<Choice> parent = slash < 0 ? item : made.getOrDefault(path.substring(0, slash).toLowerCase(Locale.ROOT), item);
                    parent.getChildren().add(child);
                    made.put(path.toLowerCase(Locale.ROOT), child);
                    remember(child, v.name() + "/" + path);
                    if (v.equals(view) && value != null && path.equalsIgnoreCase(value)) chosen = child;
                }
            }
            if (v.equals(view) && (value == null || chosen == null)) chosen = item;   // a value no note has any more: its view
            items.add(item);
        }
        views.getRoot().getChildren().setAll(items);
        if (chosen == null) chosen = items.get(0);
        for (TreeItem<Choice> up = chosen.getParent(); up != null; up = up.getParent()) up.setExpanded(true);
        view = chosen.getValue().view();
        value = chosen.getValue().value();
        views.getSelectionModel().select(chosen);
    }

    /** Keeps a branch open or closed as the user left it, across rebuilds. */
    private void remember(TreeItem<Choice> item, String key) {
        item.setExpanded(expanded.contains(key));
        item.expandedProperty().addListener((o, was, now) -> {
            if (now) expanded.add(key);
            else expanded.remove(key);
        });
    }

    private void showNotes() {
        if (view == null) return;
        boolean table = asTable.isSelected();
        List<Field> fields = type.fields(view);
        List<TreeTableColumn<Row, ?>> columns = new ArrayList<>();
        columns.add(titleColumn(table ? List.of() : fields));
        if (table) fields.forEach(f -> columns.add(column(f)));

        List<TreeItem<Row>> items = new ArrayList<>();
        for (Group g : rows(type, view, value, filter.getText(), entries)) {
            if (g.value() == null) {
                g.notes().forEach(e -> items.add(new TreeItem<>(new Row(null, null, e))));
                continue;
            }
            TreeItem<Row> heading = new TreeItem<>(new Row(g, label(view, g.value()), null));
            heading.setExpanded(true);
            g.notes().forEach(e -> heading.getChildren().add(new TreeItem<>(new Row(null, null, e))));
            items.add(heading);
        }
        updating = true;
        try {
            // A list fits its one column to the width; a table keeps its columns readable and scrolls
            // sideways. The policy goes first: set later, the list's one would already have squeezed them.
            this.table.setColumnResizePolicy(table ? TreeTableView.UNCONSTRAINED_RESIZE_POLICY
                    : TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            this.table.getColumns().setAll(columns);
            this.table.getStyleClass().remove("as-list");
            if (!table) this.table.getStyleClass().add("as-list");
            this.table.getRoot().getChildren().setAll(items);
        } finally {
            updating = false;
        }
        select(open);
    }

    private String label(View v, String value) {
        if (!value.isEmpty()) return value;
        String key = v.groupBy();
        return "No " + type.fields(v).stream().filter(f -> f.key().equals(key)).findFirst().map(Field::label).orElse(key).toLowerCase(Locale.ROOT);
    }

    /** The title, with (in the list) a second line: the note's badge, dates and choices. */
    private TreeTableColumn<Row, Row> titleColumn(List<Field> subtitle) {
        TreeTableColumn<Row, Row> c = new TreeTableColumn<>("Title");
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getValue()));
        c.setComparator(Comparator.comparing((Row r) -> r.entry() != null ? r.entry().title() : r.label(), Vault::compareNatural));
        c.setPrefWidth(240);
        c.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                setGraphic(null);
                if (empty || row == null) {
                    setText(null);
                    return;
                }
                if (row.entry() == null) {
                    setText(row.label() + "  " + row.group().notes().size());
                    return;
                }
                Note note = row.entry().note();
                String line = subtitle.stream().filter(f -> f.input() == Input.CHOICE && !f.key().equals("level") || f.input() == Input.DATE)
                        .map(f -> note.get(f.key())).filter(s -> !s.isEmpty()).collect(Collectors.joining(" · "));
                Badge badge = subtitle.isEmpty() ? null : type.badge(note);
                HBox second = new HBox(6);
                if (badge != null) second.getChildren().add(colored(new Label(badge.text()), badge.color()));
                if (!line.isEmpty()) second.getChildren().add(new Label(line));
                second.getChildren().forEach(n -> n.getStyleClass().add("note-subtitle"));
                setText(second.getChildren().isEmpty() ? row.entry().title() : null);
                if (!second.getChildren().isEmpty()) setGraphic(new VBox(1, new Label(row.entry().title()), second));
            }
        });
        return c;
    }

    private TreeTableColumn<Row, String> column(Field f) {
        TreeTableColumn<Row, String> c = new TreeTableColumn<>(f.label());
        c.setCellValueFactory(cell -> {
            Entry e = cell.getValue().getValue().entry();
            return new ReadOnlyStringWrapper(e == null ? "" : String.join(", ", type.values(e.note(), f.key())));
        });
        if (f.key().equals("difficulty") || f.key().equals("level")) {
            // In the judge's colors (the difficulty) or the level's.
            c.setCellFactory(col -> new TreeTableCell<>() {
                @Override
                protected void updateItem(String text, boolean empty) {
                    super.updateItem(text, empty);
                    setText(empty ? null : text);
                    Row row = getTableRow() == null ? null : getTableRow().getItem();
                    Badge badge = empty || row == null || row.entry() == null ? null : type.badge(row.entry().note());
                    String color = badge == null ? "" : f.key().equals("level") ? Judges.levelColor(text) : badge.color();
                    colored(this, color);
                }
            });
        }
        c.setComparator(Vault::compareNatural);
        c.setPrefWidth(switch (f.input()) {
            case LIST -> 160;
            case CHECK -> 70;
            default -> 110;
        });
        return c;
    }

    /** Paints a label's text in a color ("#RRGGBB"), in bold; "" leaves it as it is. */
    private static <T extends Labeled> T colored(T label, String color) {
        label.setStyle(color.isEmpty() ? "" : "-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        return label;
    }

    private static TreeItem<Row> find(TreeItem<Row> parent, Path p) {
        if (p == null) return null;
        for (TreeItem<Row> item : parent.getChildren()) {
            if (item.getValue().entry() != null && item.getValue().entry().path().equals(p)) return item;
            TreeItem<Row> inside = find(item, p);
            if (inside != null) return inside;
        }
        return null;
    }

    /**
     * What a view shows: its notes (of its kind, matching its "only", having the picked value,
     * containing the filter text) in its order, grouped by its property unless a value was picked.
     * A note with several values (algorithms: [dp, greedy]) is under each of them, and a value
     * with a "/" under its first part: "graphs/dijkstra" is in "graphs".
     */
    static List<Group> rows(NotebookType type, View view, String value, String filter, List<Entry> entries) {
        String wanted = fold(filter.strip());
        List<Field> fields = type.fields(view);
        List<Entry> notes = new ArrayList<>();
        for (Entry e : entries) {
            if (view.kind() != null && !type.kindOf(e.note()).id().equals(view.kind())) continue;
            if (view.only() != null && !type.matches(e.note(), view.only())) continue;
            if (value != null && !has(type.values(e.note(), view.groupBy()), value)) continue;
            if (!wanted.isEmpty() && !fold(e.title() + " " + fields.stream().map(f -> String.join(" ", type.values(e.note(), f.key())))
                    .collect(Collectors.joining(" "))).contains(wanted)) continue;
            notes.add(e);
        }
        notes.sort(order(type, view.sortBy()));
        if (view.groupBy() == null || value != null) return List.of(new Group(null, notes));

        Map<String, List<Entry>> groups = new TreeMap<>(groupOrder(fields, view.groupBy()));
        List<Entry> none = new ArrayList<>();
        for (Entry e : notes) {
            List<String> values = type.values(e.note(), view.groupBy());
            if (values.isEmpty()) none.add(e);
            for (String v : values) {
                List<Entry> group = groups.computeIfAbsent(v.split("/", 2)[0].strip(), k -> new ArrayList<>());
                if (group.isEmpty() || group.get(group.size() - 1) != e) group.add(e);
            }
        }
        List<Group> result = new ArrayList<>();
        groups.forEach((v, list) -> result.add(new Group(v, list)));
        if (!none.isEmpty()) result.add(new Group("", none));
        return result;
    }

    /**
     * How many notes of the view have each value, and each first part of one ("graphs" for
     * "graphs/dijkstra"), in the sidebar's order; "" counts those with none, last.
     */
    static Map<String, Integer> counts(NotebookType type, View view, List<Entry> entries) {
        Map<String, Set<Path>> notes = new TreeMap<>(pathOrder(type.fields(view), view.groupBy()));
        for (Group g : rows(type, view, null, "", entries)) {
            for (Entry e : g.notes()) {
                List<String> values = type.values(e.note(), view.groupBy());
                if (values.isEmpty()) notes.computeIfAbsent("", k -> new HashSet<>()).add(e.path());
                for (String v : values) {
                    String path = "";
                    for (String part : v.split("/")) {
                        path = path.isEmpty() ? part.strip() : path + "/" + part.strip();
                        notes.computeIfAbsent(path, k -> new HashSet<>()).add(e.path());
                    }
                }
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        notes.forEach((path, set) -> counts.put(path, set.size()));
        return counts;
    }

    /** A picked value matches itself and everything under it ("graphs" has "graphs/dijkstra"), in any case; "" is none. */
    private static boolean has(List<String> values, String value) {
        if (value.isEmpty()) return values.isEmpty();
        String under = value.toLowerCase(Locale.ROOT) + "/";
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(value) || v.toLowerCase(Locale.ROOT).startsWith(under));
    }

    /** "graphs" before "graphs/bfs" before "greedy"; the first part in group order; none ("") last. */
    private static Comparator<String> pathOrder(List<Field> fields, String key) {
        Comparator<String> first = groupOrder(fields, key), natural = Vault::compareNatural;
        return (a, b) -> {
            if (a.isEmpty() || b.isEmpty()) return Boolean.compare(a.isEmpty(), b.isEmpty());
            String[] x = a.split("/"), y = b.split("/");
            for (int i = 0; i < Math.min(x.length, y.length); i++) {
                int c = (i == 0 ? first : natural).compare(x[i], y[i]);
                if (c != 0) return c;
            }
            return Integer.compare(x.length, y.length);
        };
    }

    /** Values in natural order, or a choice's in the order of its options (To do, Attempted, …). */
    private static Comparator<String> groupOrder(List<Field> fields, String key) {
        List<String> options = fields.stream().filter(f -> f.key().equals(key) && f.input() == Input.CHOICE)
                .findFirst().map(Field::options).orElse(List.of());
        Comparator<String> natural = Vault::compareNatural;
        if (options.isEmpty()) return natural;
        return Comparator.comparingInt((String v) -> {
            for (int i = 0; i < options.size(); i++) if (options.get(i).equalsIgnoreCase(v)) return i;
            return options.size();
        }).thenComparing(natural);
    }

    /** "title", "modified" or a property (maybe a worked-out one); "-" in front for highest first. Notes without it go last. */
    private static Comparator<Entry> order(NotebookType type, String sortBy) {
        boolean desc = sortBy.startsWith("-");
        String key = desc ? sortBy.substring(1) : sortBy;
        Comparator<Entry> byTitle = (a, b) -> Vault.compareNatural(a.title(), b.title());
        Function<Entry, String> value = e -> String.join(", ", type.values(e.note(), key));
        Comparator<Entry> c = switch (key) {
            case "title" -> byTitle;
            case "modified" -> Comparator.comparing(Entry::modified);
            default -> (a, b) -> Vault.compareNatural(value.apply(a), value.apply(b));
        };
        if (desc) c = c.reversed();
        if (!key.equals("title") && !key.equals("modified")) {
            c = Comparator.comparing((Entry e) -> value.apply(e).isEmpty()).thenComparing(c);
        }
        return c.thenComparing(byTitle);
    }

    /** For matching: no accents, no case ("arbol" finds "Árbol"). */
    static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
