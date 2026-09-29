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

    /**
     * The note as HTML. Math ($…$ inline, $$…$$ on its own) is set aside first and left as spans
     * for KaTeX, since Markdown would read a_1 … b_1 as emphasis; math inside code stays code.
     */
    static String html(String markdown) {
        List<String> math = new ArrayList<>();
        String html = RENDERER.render(PARSER.parse(setMathAside(markdown.replace("\r\n", "\n"), math)));
        for (int i = 0; i < math.size(); i++) html = html.replace(MARK + i + MARK, math.get(i));
        return html;
    }

    /** Stands in for a piece of math while Markdown is parsed; a control character no note contains. */
    private static final String MARK = String.valueOf((char) 1);

    private static String setMathAside(String text, List<String> math) {
        StringBuilder out = new StringBuilder();
        String fence = null;   // the ``` or ~~~ of the code block we are in
        int i = 0, n = text.length();
        while (i < n) {
            if (i == 0 || text.charAt(i - 1) == '\n') {   // a fenced code block passes whole
                int end = text.indexOf('\n', i) < 0 ? n : text.indexOf('\n', i) + 1;
                String line = text.substring(i, end).stripLeading();
                if (fence != null || line.startsWith("```") || line.startsWith("~~~")) {
                    if (fence == null) fence = line.substring(0, 3);
                    else if (line.startsWith(fence)) fence = null;
                    out.append(text, i, end);
                    i = end;
                    continue;
                }
            }
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n) {   // \$ stays a dollar
                out.append(text, i, i + 2);
                i += 2;
                continue;
            }
            if (c == '`') {   // an inline code span, whole
                int run = 0;
                while (i + run < n && text.charAt(i + run) == '`') run++;
                int close = text.indexOf("`".repeat(run), i + run);
                int end = close < 0 ? i + run : close + run;
                out.append(text, i, end);
                i = end;
                continue;
            }
            if (c == '$') {
                boolean display = i + 1 < n && text.charAt(i + 1) == '$';
                int start = i + (display ? 2 : 1), close = display ? text.indexOf("$$", start) : inlineMathEnd(text, start);
                if (close > start) {
                    math.add("<span class=\"math" + (display ? " display" : "") + "\">" + escape(text.substring(start, close)) + "</span>");
                    out.append(MARK).append(math.size() - 1).append(MARK);
                    i = close + (display ? 2 : 1);
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Where inline math opened at {@code start} closes, on the same line: "$x^2$" is math, "$5 and $10" is not. */
    private static int inlineMathEnd(String text, int start) {
        if (start >= text.length() || Character.isWhitespace(text.charAt(start))) return -1;
        for (int j = start; j < text.length(); j++) {
            char c = text.charAt(j);
            if (c == '\n') return -1;
            if (c == '\\') {
                j++;
                continue;
            }
            if (c == '$') {
                boolean digitAfter = j + 1 < text.length() && Character.isDigit(text.charAt(j + 1));
                return Character.isWhitespace(text.charAt(j - 1)) || digitAfter ? -1 : j;
            }
        }
        return -1;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private Object call(String function, Object... args) {
        return ((JSObject) engine.executeScript("window")).call(function, args);
    }
}
