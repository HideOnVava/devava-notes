package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Field;
import com.devavaxp.notes.NotebookType.Kind;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.converter.LocalDateStringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The properties of the open note, as the fields of its kind in two columns. A change goes into
 * the note as soon as it is made (a text when Enter is pressed or the field is left); a list is
 * typed with commas: "dp, greedy".
 */
final class PropertiesPanel {

    final GridPane node = new GridPane();
    private final Runnable onChange;

    PropertiesPanel(Runnable onChange) {
        this.onChange = onChange;
        node.getStyleClass().add("properties");
        node.setHgap(10);
        node.setVgap(6);
        ColumnConstraints name = new ColumnConstraints(), value = new ColumnConstraints();
        name.setMinWidth(Region.USE_PREF_SIZE);   // a narrow note pane shrinks the fields, never the names
        value.setHgrow(Priority.ALWAYS);
        value.setFillWidth(true);
        node.getColumnConstraints().setAll(name, value, name, value);
    }

    void show(Kind kind, Note note) {
        node.getChildren().clear();
        List<Field> fields = kind.fields();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            Label name = new Label(f.label());
            name.getStyleClass().add("property-name");
            Node editor = editor(f, note);
            editor.setId("property-" + f.key());
            node.add(name, i % 2 * 2, i / 2);
            node.add(editor, i % 2 * 2 + 1, i / 2);
        }
        node.setVisible(!fields.isEmpty());
        node.setManaged(!fields.isEmpty());
    }

    private Node editor(Field f, Note note) {
        String key = f.key(), current = note.get(key);
        return switch (f.input()) {
            case TEXT -> text(current, s -> note.set(key, s));
            case LIST -> text(current, s -> note.setList(key, Arrays.stream(s.split(",")).map(String::strip).filter(x -> !x.isEmpty()).toList()));
            case CHOICE -> {
                ComboBox<String> box = new ComboBox<>();
                box.getItems().add("");   // no value
                box.getItems().addAll(f.options());
                if (!current.isEmpty() && !box.getItems().contains(current)) box.getItems().add(current);
                box.setValue(current);
                box.setMaxWidth(Double.MAX_VALUE);
                box.setOnAction(e -> change(() -> note.set(key, box.getValue() == null ? "" : box.getValue())));
                yield box;
            }
            case DATE -> {
                DatePicker picker = new DatePicker(date(current));
                picker.setConverter(new LocalDateStringConverter(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ISO_LOCAL_DATE));
                picker.setPromptText("yyyy-mm-dd");
                picker.setMinWidth(130);
                picker.setMaxWidth(Double.MAX_VALUE);
                picker.valueProperty().addListener((o, was, now) -> change(() -> note.set(key, now == null ? "" : now.toString())));
                yield picker;
            }
            case CHECK -> {
                CheckBox box = new CheckBox();
                box.setSelected(current.equals("true"));
                box.setOnAction(e -> change(() -> note.set(key, box.isSelected() ? "true" : "")));
                yield box;
            }
        };
    }

    private TextField text(String value, Consumer<String> set) {
        TextField field = new TextField(value);
        String[] saved = {value};
        Runnable commit = () -> {
            if (field.getText().equals(saved[0])) return;
            saved[0] = field.getText();
            change(() -> set.accept(field.getText()));
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((o, was, focused) -> {
            if (!focused) commit.run();
        });
        return field;
    }

    private void change(Runnable edit) {
        edit.run();
        onChange.run();
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
