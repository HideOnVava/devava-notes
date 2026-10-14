package com.devavaxp.notes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The plan of Ingeniería en Sistemas Computacionales at the Instituto Tecnológico de La Laguna: its
 * 55 subjects as Devava Kardex has them, each with the notebook type, code language and topics this
 * app proposes for it (reticula-isc.json).
 */
final class Reticula {

    /** A subject: its key in the plan ("B12"), its name and semester, and what its notebook starts with. */
    record Subject(String key, String name, int semester, NotebookType type, String language, List<String> topics) {
    }

    static final List<Subject> ISC = load();

    private Reticula() {
    }

    /** The subject a notebook is for: the one it was made from, else the one with its name (accents and case aside). */
    static Subject of(Path notebook) {
        String key = Vault.meta(notebook, "subject"), name = NotebookView.fold(notebook.getFileName().toString());
        for (Subject s : ISC) if (s.key().equals(key)) return s;
        for (Subject s : ISC) if (NotebookView.fold(s.name()).equals(name)) return s;
        return null;
    }

    /** "1er semestre", "2do semestre"…, as the Tec writes them. */
    static String semester(int n) {
        String[] ordinals = {"1er", "2do", "3er", "4to", "5to", "6to", "7mo", "8vo", "9no"};
        return (n >= 1 && n <= ordinals.length ? ordinals[n - 1] : n + "º") + " semestre";
    }

    private static List<Subject> load() {
        try (InputStream in = Reticula.class.getResourceAsStream("reticula-isc.json")) {
            List<Subject> subjects = new ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                List<String> topics = new ArrayList<>();
                o.getAsJsonArray("temas").forEach(t -> topics.add(t.getAsString()));
                subjects.add(new Subject(o.get("clave").getAsString(), o.get("nombre").getAsString(), o.get("semestre").getAsInt(),
                        NotebookType.byId(o.get("tipo").getAsString()), o.get("lenguaje").getAsString(), List.copyOf(topics)));
            }
            return List.copyOf(subjects);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
