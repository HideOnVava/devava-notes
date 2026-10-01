package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Field;
import com.devavaxp.notes.NotebookType.Kind;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.converter.LocalDateStringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.devavaxp.notes.Text.t;

/**
 * The properties of the open note, as the fields of its kind in two columns. A change goes into
 * the note as soon as it is made (a text when Enter is pressed or the field is left); a list is
 * typed with commas ("dp, graphs/dijkstra") or picked from its ▾ menu.
 */
final class PropertiesPanel {

    final GridPane node = new GridPane();
    private final Consumer<String> onChange;
    private final Consumer<String> openLink;
    private final Function<String, List<String>> used;
    /** The name and the editor of each property, laid out in one or two pairs of columns. */
    private final List<Node[]> cells = new ArrayList<>();
    private int columns;

    /**
     * {@code onChange} gets the key of each property that changed; {@code used} gives the values a
     * list property already has in this notebook, offered next to its suggestions.
     */
    PropertiesPanel(Consumer<String> onChange, Consumer<String> openLink, Function<String, List<String>> used) {
        this.onChange = onChange;
        this.openLink = openLink;
        this.used = used;
        node.getStyleClass().add("properties");
        node.setHgap(10);
        node.setVgap(6);
        ColumnConstraints name = new ColumnConstraints(), value = new ColumnConstraints();
        name.setMinWidth(Region.USE_PREF_SIZE);   // a narrow note pane shrinks the fields, never the names
        value.setHgrow(Priority.ALWAYS);
        value.setFillWidth(true);
        node.getColumnConstraints().setAll(name, value, name, value);
        node.widthProperty().addListener((o, was, now) -> Platform.runLater(() -> arrange(fit(now.doubleValue()))));
    }

    void show(Kind kind, Note note) {
        cells.clear();
        for (Field f : kind.fields()) {
            Label name = new Label(t(f.label()));
            name.getStyleClass().add("property-name");
            cells.add(new Node[]{name, editor(f, note)});
        }
        columns = 0;
        arrange(fit(node.getWidth()));
        node.setVisible(!cells.isEmpty());
        node.setManaged(!cells.isEmpty());
    }

    /** Two pairs of columns when there is room; one when the note pane is narrow (next to a table). */
    private static int fit(double width) {
        return width > 0 && width < 620 ? 1 : 2;
    }

    private void arrange(int columns) {
        if (columns == this.columns) return;
        this.columns = columns;
        node.getChildren().clear();
        for (int i = 0; i < cells.size(); i++) {
            node.add(cells.get(i)[0], i % columns * 2, i / columns);
            node.add(cells.get(i)[1], i % columns * 2 + 1, i / columns);
        }
    }

    private Node editor(Field f, Note note) {
        String key = f.key(), current = note.get(key);
        return switch (f.input()) {
            case TEXT -> {
                TextField field = text(key, current, s -> note.set(key, s));
                if (!key.equals("url")) yield field;
                Button open = new Button("↗");
                open.getStyleClass().add("flat");
                open.setTooltip(new Tooltip(t("Open the link")));
                open.setOnAction(e -> openLink.accept(note.get("url")));
                yield row(field, open);
            }
            case LIST -> {
                TextField field = text(key, current, s -> note.setList(key, items(s)));
                yield f.options().isEmpty() && used.apply(key).isEmpty() ? field : row(field, picker(f, note, field));
            }
            case CHOICE -> {
                ComboBox<String> box = new ComboBox<>();
                box.getItems().add("");   // no value
                box.getItems().addAll(f.options());
                if (!current.isEmpty() && !box.getItems().contains(current)) box.getItems().add(current);
                box.setValue(current);
                box.setMaxWidth(Double.MAX_VALUE);
                box.setCellFactory(list -> new ListCell<>() {   // the choice in the app's language; the note keeps it in English
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : t(item));
                    }
                });
                box.setButtonCell(box.getCellFactory().call(null));
                box.setOnAction(e -> change(key, () -> note.set(key, box.getValue() == null ? "" : box.getValue())));
                yield id(box, key);
            }
            case DATE -> {
                DatePicker picker = new DatePicker(date(current));
                picker.setConverter(new LocalDateStringConverter(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ISO_LOCAL_DATE));
                picker.setPromptText(t("yyyy-mm-dd"));
                picker.setMinWidth(130);
                picker.setMaxWidth(Double.MAX_VALUE);
                picker.valueProperty().addListener((o, was, now) -> change(key, () -> note.set(key, now == null ? "" : now.toString())));
                yield id(picker, key);
            }
            case CHECK -> {
                CheckBox box = new CheckBox();
                box.setSelected(current.equals("true"));
                box.setOnAction(e -> change(key, () -> note.set(key, box.isSelected() ? "true" : "")));
                yield id(box, key);
            }
        };
    }

    /** The ▾ menu of a list: its suggestions and the notebook's own values, ticked when the note has them. */
    private MenuButton picker(Field f, Note note, TextField field) {
        MenuButton pick = new MenuButton("▾");
        pick.getStyleClass().add("picker");
        pick.setOnShowing(e -> {
            List<String> chosen = note.list(f.key());
            Set<String> values = new TreeSet<>(Vault::compareNatural);
            values.addAll(chosen);
            values.addAll(used.apply(f.key()));
            values.addAll(f.options());
            pick.getItems().clear();
            for (String value : values) {
                CheckMenuItem item = new CheckMenuItem(value);
                item.setSelected(chosen.stream().anyMatch(value::equalsIgnoreCase));
                item.setOnAction(a -> {
                    List<String> now = new ArrayList<>(note.list(f.key()));
                    if (!now.removeIf(value::equalsIgnoreCase)) now.add(value);
                    field.setText(String.join(", ", now));
                    field.getProperties().put("saved", field.getText());
                    change(f.key(), () -> note.setList(f.key(), now));
                });
                pick.getItems().add(item);
            }
        });
        return pick;
    }

    private TextField text(String key, String value, Consumer<String> set) {
        TextField field = id(new TextField(value), key);
        field.getProperties().put("saved", value);
        Runnable commit = () -> {
            if (field.getText().equals(field.getProperties().get("saved"))) return;
            field.getProperties().put("saved", field.getText());
            change(key, () -> set.accept(field.getText()));
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((o, was, focused) -> {
            if (!focused) commit.run();
        });
        return field;
    }

    private void change(String key, Runnable edit) {
        edit.run();
        onChange.accept(key);
    }

    private static <T extends Node> T id(T node, String key) {
        node.setId("property-" + key);
        return node;
    }

    private static HBox row(Region grows, Node beside) {
        HBox row = new HBox(4, grows, beside);
        HBox.setHgrow(grows, Priority.ALWAYS);
        grows.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private static List<String> items(String text) {
        return Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** A date that is not yyyy-mm-dd (typed elsewhere) shows empty, and stays as it is until one is picked. */
    private static LocalDate date(String value) {
        try {
            return value.isEmpty() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
