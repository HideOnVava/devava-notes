package com.devavaxp.notes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {

    /** Every choice a note can keep shows in Spanish; the judges are names, but "Other" is "Otro". */
    @Test
    void everyChoiceShowsInSpanish() {
        List<String> english = new ArrayList<>();
        for (NotebookType type : NotebookType.ALL) {
            for (NotebookType.Kind kind : type.kinds()) {
                for (NotebookType.Field f : kind.fields()) {
                    if (f.input() != NotebookType.Input.CHOICE || f.key().equals("judge")) continue;
                    f.options().stream().filter(o -> Text.shown(o).equals(o) && !english.contains(o)).forEach(english::add);
                }
            }
        }
        assertEquals(List.of(), english);
        assertEquals("Otro", Text.shown("Other"));
        assertEquals("graphs/dijkstra", Text.shown("graphs/dijkstra"));   // what the student wrote stays as written
    }
}
