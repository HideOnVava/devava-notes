package com.devavaxp.notes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A note's text as its properties and its body. The properties are the YAML front matter between
 * "---" lines at the top, as Obsidian writes it, read in the forms people and Obsidian use:
 * {@code key: value}, {@code key: [a, b]}, or "- item" lines under the key. Setting a property
 * rewrites only that key's lines, so anything else up there (comments, nested YAML) stays as it was.
 */
final class Note {

    private final List<String> header;
    String body;

    private Note(List<String> header, String body) {
        this.header = header;
        this.body = body;
    }

    static Note parse(String text) {
        String t = text.replace("\r\n", "\n");
        if (t.startsWith("---\n")) {
            for (int close = t.indexOf("\n---", 3); close >= 0; close = t.indexOf("\n---", close + 1)) {
                int after = close + 4;
                if (after < t.length() && t.charAt(after) != '\n') continue;   // "---x" does not close it
                List<String> header = close <= 3 ? new ArrayList<>() : new ArrayList<>(List.of(t.substring(4, close).split("\n", -1)));
                return new Note(header, after < t.length() ? t.substring(after + 1) : "");
            }
        }
        return new Note(new ArrayList<>(), text);
    }

    String text() {
        return header.isEmpty() ? body : "---\n" + String.join("\n", header) + "\n---\n" + body;
    }

    /**
     * A #tag written in the text, as Obsidian reads them: after a space or a line start (not in
     * C#, a URL's /#part or &#38;), with at least one letter (#1 is not a tag), and "/" for nesting.
     */
    static final Pattern TAG = Pattern.compile("(?<![\\p{L}\\p{N}_/#&])#([\\p{L}\\p{N}_][\\p{L}\\p{N}_/-]*)");
    private static final Pattern CODE = Pattern.compile("(?ms)^[ \\t]*(```|~~~).*?^[ \\t]*\\1[^\\n]*$|`+[^`\\n]*`+");

    /** The #tags written in the body, outside code, each once. */
    List<String> inlineTags() {
        Matcher m = TAG.matcher(CODE.matcher(body.replace("\r\n", "\n")).replaceAll(" "));
        List<String> tags = new ArrayList<>();
        while (m.find()) {
            String tag = m.group(1);
            if (tag.codePoints().anyMatch(Character::isLetter) && tags.stream().noneMatch(tag::equalsIgnoreCase)) tags.add(tag);
        }
        return tags;
    }

    /** A fenced block of code: its language ("cpp", or "") and its lines. */
    record Code(String language, String text) {
    }

    private static final Pattern FENCE = Pattern.compile("(?m)^```([\\w+#.-]*)[^\\n]*\\n([\\s\\S]*?)\\n?^```");

    /** The first block of code in the body, as a snippet is inserted. */
    Optional<Code> firstCode() {
        Matcher m = FENCE.matcher(body.replace("\r\n", "\n"));
        return m.find() ? Optional.of(new Code(m.group(1), m.group(2))) : Optional.empty();
    }

    /** A property as text: its value, or its items joined by ", "; "" when it is not there. */
    String get(String key) {
        return String.join(", ", list(key));
    }

    /** A property's items: the items of a list, or a single value as one item. */
    List<String> list(String key) {
        int i = find(key);
        if (i < 0) return List.of();
        String value = uncomment(header.get(i).substring(key.length() + 1).strip());
        if (value.startsWith("[") && value.endsWith("]")) return split(value.substring(1, value.length() - 1));
        if (!value.isEmpty()) return List.of(unquote(value));
        List<String> items = new ArrayList<>();
        for (int j = i + 1; j < header.size() && header.get(j).strip().startsWith("- "); j++) {
            String item = unquote(uncomment(header.get(j).strip().substring(2).strip()));
            if (!item.isEmpty()) items.add(item);
        }
        return items;
    }

    /** Sets a property to one value, or removes it when the value is blank. */
    void set(String key, String value) {
        replace(key, value.isBlank() ? null : key + ": " + quote(value.strip(), false));
    }

    /** Sets a property to a list, or removes it when the list is empty. */
    void setList(String key, List<String> items) {
        replace(key, items.isEmpty() ? null
                : key + ": [" + items.stream().map(s -> quote(s, true)).collect(Collectors.joining(", ")) + "]");
    }

    private void replace(String key, String line) {
        int i = find(key);
        if (i < 0) {
            if (line != null) header.add(line);
            return;
        }
        int end = i + 1;   // the key's own lines: list items and anything indented under it
        while (end < header.size() && (header.get(end).startsWith(" ") || header.get(end).startsWith("\t")
                || header.get(end).startsWith("-"))) end++;
        header.subList(i, end).clear();
        if (line != null) header.add(i, line);
    }

    private int find(String key) {
        for (int i = 0; i < header.size(); i++) {
            String line = header.get(i);
            if (line.startsWith(key + ":") && (line.length() == key.length() + 1 || line.charAt(key.length() + 1) == ' ')) return i;
        }
        return -1;
    }

    /** Plain when YAML reads it back as the same text; in double quotes otherwise. */
    private static String quote(String s, boolean inList) {
        boolean quote = s.isEmpty() || "-?:,[]{}#&*!|>'\"%@`".indexOf(s.charAt(0)) >= 0
                || s.contains(": ") || s.contains(" #") || s.endsWith(":")
                || inList && s.chars().anyMatch(c -> ",[]{}".indexOf(c) >= 0);
        return quote ? "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : s;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) return s.substring(1, s.length() - 1).replace("''", "'");
        return s;
    }

    /** "800 # from the site" is 800; a quoted value keeps its #. */
    private static String uncomment(String value) {
        if (value.startsWith("\"") || value.startsWith("'")) return value;
        int hash = value.indexOf(" #");
        return hash >= 0 ? value.substring(0, hash).strip() : value;
    }

    /** The items of a [flow, "list"], split on the commas outside quotes. */
    private static List<String> split(String inner) {
        List<String> items = new ArrayList<>();
        StringBuilder item = new StringBuilder();
        char quote = 0;
        for (char c : (inner + ",").toCharArray()) {
            if (quote == 0 && c == ',') {
                String s = unquote(item.toString().strip());
                if (!s.isEmpty()) items.add(s);
                item.setLength(0);
                continue;
            }
            if (quote == 0 && (c == '"' || c == '\'')) quote = c;
            else if (c == quote) quote = 0;
            item.append(c);
        }
        return items;
    }
}
