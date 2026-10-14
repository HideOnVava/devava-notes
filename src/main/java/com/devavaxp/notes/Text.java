package com.devavaxp.notes;

import java.util.Locale;
import java.util.Map;

/**
 * The app speaks Spanish. What notes keep stays as earlier versions wrote it ("status: Solved"),
 * so their files never change meaning; this is how those choices show on screen.
 */
final class Text {

    static final Locale SPANISH = Locale.forLanguageTag("es");

    private static final Map<String, String> SHOWN = Map.ofEntries(
            Map.entry("Pending", "Pendiente"),
            Map.entry("Done", "Hecha"),
            Map.entry("To do", "Por hacer"),
            Map.entry("Attempted", "Intentado"),
            Map.entry("Solved with help", "Resuelto con ayuda"),
            Map.entry("Solved", "Resuelto"),
            Map.entry("Easy", "Fácil"),
            Map.entry("Medium", "Medio"),
            Map.entry("Hard", "Difícil"),
            Map.entry("Very hard", "Muy difícil"),
            Map.entry("Other", "Otro"));

    private Text() {
    }

    /** A choice as the app shows it: "Solved" is "Resuelto"; anything else shows as it is. */
    static String shown(String value) {
        return SHOWN.getOrDefault(value, value);
    }
}
