package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Badge;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * What is coming in a course (a Class Notes or a Databases notebook): the day each note is about,
 * which ones are still ahead, and how near they are, said the way a student would read it.
 */
final class Agenda {

    private static final String LATE = "#DC2626", SOON = "#B45309", LATER = "#6E6E78", DONE = "#15803D", PAST = "#9A9AA5";

    private Agenda() {
    }

    /** The day a note is about: an assignment's due date, any other note's date; null without one. */
    static LocalDate when(Note note) {
        String value = note.get(kind(note).equals("assignment") ? "due" : "date");
        try {
            return value.isEmpty() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;   // written some other way elsewhere: no day to put it on
        }
    }

    /** Still to do: an assignment that is not done (even a late one), or an exam from today on. */
    static boolean upcoming(Note note, LocalDate today) {
        LocalDate when = when(note);
        return when != null && switch (kind(note)) {
            case "assignment" -> !done(note);
            case "exam" -> !when.isBefore(today);
            default -> false;
        };
    }

    static boolean late(Note note, LocalDate today) {
        return kind(note).equals("assignment") && !done(note) && when(note) != null && when(note).isBefore(today);
    }

    /** "Vence en 2 días", "3 días de retraso", "Hecha", "En 5 días", "Calificación 95"… in red, amber or grey; null for a lecture. */
    static Badge badge(Note note, LocalDate today) {
        String kind = kind(note);
        LocalDate when = when(note);
        if (kind.equals("assignment")) {
            if (done(note)) return new Badge("Hecha", DONE);
            if (when == null) return null;
            long days = ChronoUnit.DAYS.between(today, when);
            if (days < 0) return new Badge(-days == 1 ? "1 día de retraso" : String.format("%d días de retraso", -days), LATE);
            if (days == 0) return new Badge("Vence hoy", LATE);
            if (days == 1) return new Badge("Vence mañana", SOON);
            return days < 7 ? new Badge(String.format("Vence en %d días", days), SOON) : new Badge(String.format("Vence el %s", day(when)), LATER);
        }
        if (kind.equals("exam")) {
            if (!note.get("grade").isEmpty()) return new Badge(String.format("Calificación %s", note.get("grade")), LATER);
            if (when == null) return null;
            long days = ChronoUnit.DAYS.between(today, when);
            if (days < 0) return new Badge("Pasado", PAST);
            if (days == 0) return new Badge("Hoy", LATE);
            if (days == 1) return new Badge("Mañana", SOON);
            return days < 7 ? new Badge(String.format("En %d días", days), SOON) : new Badge(day(when), LATER);
        }
        return null;
    }

    /** "Oct 9", "9 oct". */
    private static String day(LocalDate date) {
        return DateTimeFormatter.ofPattern("d MMM", Text.SPANISH).format(date);
    }

    private static String kind(Note note) {
        return NotebookType.CLASS_NOTES.kindOf(note).id();
    }

    private static boolean done(Note note) {
        return note.get("status").equalsIgnoreCase("Done");
    }
}
