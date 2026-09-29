package com.devavaxp.notes;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The open note, in one WebView page (editor.html): CodeMirror 6 to write it, or the note
 * rendered to read it. The page reports to Java only as alert() text ("ready", "changed",
 * "link:url", "copy:text", "error:message"), so no Java object is ever within reach of JavaScript.
 */
final class Editor {

    private static final List<Extension> MARKDOWN = List.of(TablesExtension.create(),
            StrikethroughExtension.create(), TaskListItemsExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(MARKDOWN).build();
    /** HTML written in a note shows as text and javascript: links are dropped: a note never runs code. */
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(MARKDOWN)
            .escapeHtml(true).sanitizeUrls(true).build();

    final WebView view = new WebView();
    /** What editor.js reported as failing (also printed to stderr). */
    final List<String> errors = new ArrayList<>();
    private final WebEngine engine = view.getEngine();
    private final String page = Editor.class.getResource("editor.html").toExternalForm();
    /** The page said "ready" and has the note: until then, calls only keep what to show. */
    boolean ready;
    private boolean reading;
    private String text = "";

    /** {@code onChange}: the text was edited. {@code onLink}: a link was followed; the page itself never leaves. */
    Editor(Runnable onChange, Consumer<String> onLink) {
        engine.setOnAlert(e -> {
            String message = e.getData();
            if (message.equals("changed")) {
                onChange.run();
            } else if (message.equals("ready")) {
                Platform.runLater(this::start);   // not from inside the page's own script
            } else if (message.startsWith("link:")) {
                Platform.runLater(() -> onLink.accept(message.substring(5)));
            } else if (message.startsWith("copy:")) {   // the Copy button of a code block (the page has no clipboard of its own)
                Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, message.substring(5)));
            } else if (message.startsWith("error:")) {
                report(message.substring(6));
            }
        });
        engine.setCreatePopupHandler(features -> null);
        // Anything that navigates away (a dropped file, "Open Link" in the context menu) opens outside instead.
        engine.locationProperty().addListener((o, old, location) -> {
            if (location == null || location.isEmpty() || ours(location)) return;
            onLink.accept(location);
            Platform.runLater(() -> engine.load(page));
        });
        // A (re)load starts the page empty; "ready" gives it the note again.
        engine.getLoadWorker().stateProperty().addListener((o, old, state) -> {
            if (state == Worker.State.RUNNING) ready = false;
            if (state == Worker.State.SUCCEEDED) Platform.runLater(() -> {
                if (!ready && ours(engine.getLocation())) report("the editor did not load");
            });
        });
        engine.load(page);
    }

    /** Whether the WebView shows editor.html; it reports file:/C:/… as file:///C:/…, so both are compared that way. */
    private boolean ours(String location) {
        return location != null && location.replace(":///", ":/").equals(page.replace(":///", ":/"));
    }

    private void start() {
        ready = ours(engine.getLocation()) && "function".equals(engine.executeScript("typeof setText"));
        if (ready) open(text);
    }

    private void report(String error) {
        errors.add(error);
        System.err.println("[editor.js] " + error);
    }

    /** Shows a note; its undo history starts fresh. */
    void open(String text) {
        this.text = text;
        if (!ready) return;
        call("setText", text);
        if (reading) call("showReading", html(text));
    }

    /** The text as it is now in the editor. */
    String text() {
        if (ready) text = (String) call("getText");
        return text;
    }

    void setReading(boolean reading) {
        this.reading = reading;
        if (!ready) return;
        if (reading) call("showReading", html(text()));
        else call("showEditor");
    }

    void focus() {
        view.requestFocus();
        if (ready && !reading) call("focusEditor");
    }

    /** Types text at the cursor, as if the user had. */
    void insert(String text) {
        if (ready && !reading) call("insertText", text);
    }

    /** Puts code at the cursor: as it is inside a code block, in a new ```language block outside one. */
    void insertCode(String code, String language) {
        if (ready && !reading) call("insertCode", code, language);
    }

    static String html(String markdown) {
        return RENDERER.render(PARSER.parse(markdown));
    }

    private Object call(String function, Object... args) {
        return ((JSObject) engine.executeScript("window")).call(function, args);
    }
}
