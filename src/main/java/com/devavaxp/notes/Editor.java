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

import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The open note, in one WebView page (editor.html): CodeMirror 6 to write it, or the note
 * rendered to read it. The page reports to Java only as alert() text ("ready", "changed",
 * "link:url", "open:title", "tag:name", "copy:text", "error:message"), so no Java object is ever
 * within reach of JavaScript.
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
    private final String page = page();
    /** The page said "ready" and has the note: until then, calls only keep what to show. */
    boolean ready;
    private boolean reading, dark;
    private String text = "";
    /** The open note's folder, where its relative images (attachments/…) are. */
    private Path base;
    private List<String> titles = List.of();
    private Set<String> known = Set.of();

    /**
     * {@code onChange}: the text was edited. {@code onLink}: a link was followed (the page itself
     * never leaves). {@code onOpen}: a [[link]] to a note. {@code onTag}: a #tag.
     */
    Editor(Runnable onChange, Consumer<String> onLink, Consumer<String> onOpen, Consumer<String> onTag) {
        engine.setOnAlert(e -> {
            String message = e.getData();
            if (message.equals("changed")) {
                onChange.run();
            } else if (message.equals("ready")) {
                Platform.runLater(this::start);   // not from inside the page's own script
            } else if (message.startsWith("link:")) {
                Platform.runLater(() -> onLink.accept(message.substring(5)));
            } else if (message.startsWith("open:")) {
                Platform.runLater(() -> onOpen.accept(message.substring(5)));
            } else if (message.startsWith("tag:")) {
                Platform.runLater(() -> onTag.accept(message.substring(4)));
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
            if (state == Worker.State.FAILED) report("the editor did not load: " + engine.getLoadWorker().getException());
            if (state == Worker.State.SUCCEEDED) Platform.runLater(() -> {
                if (!ready && ours(engine.getLocation())) report("the editor did not load");
            });
        });
        engine.load(page);
    }

    /**
     * The page: the copy next to the installed app's jar (build-windows.ps1 puts it there, since a
     * page inside a jar can neither run its scripts nor show the notes' pictures), else the one
     * among the classes.
     */
    private static String page() {
        try {
            Path copy = Path.of(Editor.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .resolveSibling("editor").resolve("editor.html");
            if (Files.isRegularFile(copy)) return copy.toUri().toString();
        } catch (URISyntaxException | RuntimeException ignored) {
            // No location to look next to: the page among the classes.
        }
        return Editor.class.getResource("editor.html").toExternalForm();
    }

    /** Whether the WebView shows editor.html; it reports file:/C:/… as file:///C:/…, so both are compared that way. */
    private boolean ours(String location) {
        return location != null && location.replace(":///", ":/").equals(page.replace(":///", ":/"));
    }

    private void start() {
        ready = ours(engine.getLocation()) && "function".equals(engine.executeScript("typeof setText"));
        if (!ready) return;
        call("setTheme", dark ? "dark" : "light");
        call("setTitles", String.join("\n", titles));
        open(text, base);
    }

    private void report(String error) {
        errors.add(error);
        System.err.println("[editor.js] " + error);
    }

    /** Shows a note (its undo history starts fresh); {@code folder} is where its relative images are. */
    void open(String text, Path folder) {
        this.text = text;
        this.base = folder;
        if (!ready) return;
        call("setText", text);
        if (reading) call("showReading", html());
    }

    /** The titles offered after [[, and known when a [[link]] is drawn (an unknown one looks missing). */
    void setTitles(Collection<String> titles) {
        this.titles = List.copyOf(titles);
        Set<String> lower = new HashSet<>();
        titles.forEach(t -> lower.add(t.toLowerCase(Locale.ROOT)));
        this.known = lower;
        if (ready) call("setTitles", String.join("\n", this.titles));
    }

    void setDark(boolean dark) {
        this.dark = dark;
        if (ready) call("setTheme", dark ? "dark" : "light");
    }

    /** The text as it is now in the editor. */
    String text() {
        if (ready) text = (String) call("getText");
        return text;
    }

    void setReading(boolean reading) {
        this.reading = reading;
        if (!ready) return;
        if (reading) call("showReading", html());
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

    /** Selects the first place with the text (accents and case aside), as Search found it. */
    void selectMatch(String found) {
        if (ready && !reading) call("selectMatch", found);
    }

    private String html() {
        Set<String> names = known;
        return html(text(), base, title -> names.contains(title.toLowerCase(Locale.ROOT)));
    }

    static String html(String markdown) {
        return html(markdown, null, title -> true);
    }

    /**
     * The note as HTML. Math ($…$ inline, $$…$$ on its own), [[links]] and #tags are set aside
     * first, since Markdown would read a_1 … b_1 as emphasis; inside code they stay code. Relative
     * images are looked for in {@code folder}.
     */
    static String html(String markdown, Path folder, Predicate<String> exists) {
        List<String> pieces = new ArrayList<>();
        String html = RENDERER.render(PARSER.parse(setAside(markdown.replace("\r\n", "\n"), pieces, exists)));
        for (int i = 0; i < pieces.size(); i++) html = html.replace(MARK + i + MARK, pieces.get(i));
        return folder == null ? html : IMAGE.matcher(html).replaceAll(m -> Matcher.quoteReplacement(
                "<img src=\"" + resolve(folder, m.group(1).replace("&amp;", "&")).replace("&", "&amp;") + "\""));
    }

    /** attachments/a b.png (or a%20b.png), next to the note, as a file: URL; anything with a scheme stays as it is. */
    private static String resolve(Path folder, String src) {
        if (src.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) return src;
        try {
            return folder.resolve(URLDecoder.decode(src.replace("+", "%2B"), StandardCharsets.UTF_8)).normalize().toUri().toString();
        } catch (IllegalArgumentException e) {   // not a path this system accepts
            return src;
        }
    }

    private static final Pattern IMAGE = Pattern.compile("<img src=\"([^\"]*)\"");

    /** Stands in for a piece set aside while Markdown is parsed; a control character no note contains. */
    private static final String MARK = String.valueOf((char) 1);

    private static String setAside(String text, List<String> pieces, Predicate<String> exists) {
        StringBuilder out = new StringBuilder();
        String fence = null;   // the ``` or ~~~ of the code block we are in
        Matcher tag = Note.TAG.matcher(text).useTransparentBounds(true).useAnchoringBounds(false);
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
            if (c == '\\' && i + 1 < n) {   // \$ stays a dollar, \# a hash
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
            String piece = null;
            int after = i;
            if (c == '$') {
                boolean display = i + 1 < n && text.charAt(i + 1) == '$';
                int start = i + (display ? 2 : 1), close = display ? text.indexOf("$$", start) : inlineMathEnd(text, start);
                if (close > start) {
                    piece = "<span class=\"math" + (display ? " display" : "") + "\">" + escape(text.substring(start, close)) + "</span>";
                    after = close + (display ? 2 : 1);
                }
            } else if (c == '[' && text.startsWith("[[", i)) {
                int close = text.indexOf("]]", i + 2), line = text.indexOf('\n', i);
                if (close > i + 2 && (line < 0 || close < line)) {
                    String[] parts = text.substring(i + 2, close).split("\\|", 2);
                    String title = parts[0].strip(), label = parts.length > 1 ? parts[1].strip() : title;
                    if (!title.isEmpty()) {
                        piece = "<a href=\"#\" class=\"wikilink" + (exists.test(title) ? "" : " missing") + "\" data-note=\""
                                + escape(title) + "\">" + escape(label.isEmpty() ? title : label) + "</a>";
                        after = close + 2;
                    }
                }
            } else if (c == '#' && tag.region(i, n).lookingAt() && tag.group(1).codePoints().anyMatch(Character::isLetter)) {
                piece = "<span class=\"tag\" data-tag=\"" + escape(tag.group(1)) + "\">#" + escape(tag.group(1)) + "</span>";
                after = tag.end();
            }
            if (piece != null) {
                pieces.add(piece);
                out.append(MARK).append(pieces.size() - 1).append(MARK);
                i = after;
            } else {
                out.append(c);
                i++;
            }
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
