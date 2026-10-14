package com.devavaxp.notes;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.util.Map;

/** Line icons drawn on 24 units, stroked and never filled; CSS (.icon) gives them their color. */
final class Icons {

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("book", "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5z M4 19a2 2 0 0 1 2-2h13"),
            Map.entry("code", "M9 7l-5 5 5 5M15 7l5 5-5 5"),
            Map.entry("database", "M5 5.5a7 2.8 0 1 0 14 0a7 2.8 0 1 0-14 0z M5 5.5v13c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8v-13"
                    + " M5 12c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8"),
            Map.entry("trophy", "M8 4h8v5a4 4 0 0 1-8 0V4z M8 6H5a3 3 0 0 0 3 3 M16 6h3a3 3 0 0 1-3 3 M12 13v4 M8 20h8"),
            Map.entry("note", "M6 3h9l4 4v14H6z M14 3v5h5 M9 13h7 M9 17h5"),
            Map.entry("calendar", "M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z M4 10h16 M9 3v4 M15 3v4"),
            Map.entry("task", "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z M8.5 12.5l2.5 2.5 4.5-5"),
            Map.entry("exam", "M7 3h10v18H7z M10 8h4 M10 12h4 M10 16h2"),
            Map.entry("snippet", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z M8 10l-2 2 2 2 M12 14h4"),
            Map.entry("table", "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M3 10h18 M9 10v10"),
            Map.entry("list", "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"),
            Map.entry("layers", "M12 3l9 5-9 5-9-5 9-5z M3 13l9 5 9-5"),
            Map.entry("tag", "M3 12V4h8l10 10-8 8L3 12z M7.5 7.5h.01"),
            Map.entry("star", "M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6l-5.4 2.9 1.2-6-4.5-4.2 6.1-.7z"),
            Map.entry("search", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0-12 0z M20 20l-4.5-4.5"),
            Map.entry("plus", "M12 5v14M5 12h14"),
            Map.entry("copy", "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z"
                    + " M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"),
            Map.entry("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"),
            Map.entry("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0z"),
            Map.entry("pen", "M4 20h4L19 9l-4-4L4 16v4z"),
            Map.entry("back", "M15 6l-6 6 6 6"),
            Map.entry("right", "M9 6l6 6-6 6"),
            Map.entry("down", "M6 9l6 6 6-6"),
            Map.entry("help", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .9-1 1.7 M12 17h.01"),
            Map.entry("options", "M4 7h9M17 7h3M4 17h3M11 17h9M15 5v4M9 15v4"),
            Map.entry("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"));

    private Icons() {
    }

    /** An icon {@code size} pixels square; the whole 24-unit square counts, so every icon lines up. */
    static Node of(String name, double size) {
        SVGPath path = new SVGPath();
        path.setContent(PATHS.get(name));
        path.getStyleClass().add("icon");
        Group drawing = new Group(new Rectangle(24, 24, Color.TRANSPARENT), path);
        drawing.setScaleX(size / 24);
        drawing.setScaleY(size / 24);
        return new Group(drawing);
    }
}
