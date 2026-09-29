package com.devavaxp.notes;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.filechooser.FileSystemView;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The notes on disk: under Documents/Devava Notes, one folder per notebook and one Markdown
 * file per note, whose file name is its title; the notebook's type is in its .notebook.json.
 * {@code -Dnotes.home=path} moves it all elsewhere. A note is written to a temporary file that
 * then replaces it, so a crash never leaves it cut.
 */
final class Vault {

    static final String EXT = ".md";
    private static final String META = ".notebook.json", SETTINGS = "settings.json";

    /** A notebook as the home screen lists it. */
    record Notebook(Path dir, NotebookType type, int notes, FileTime edited) {
        String name() {
            return dir.getFileName().toString();
        }
    }

    /** "Lecture 2" before "Lecture 10": runs of digits compare by value (from Devava Reader). */
    static final Comparator<Path> BY_NAME = (a, b) -> compareNatural(title(a), title(b));

    final Path root;

    Vault(Path root) {
        this.root = root;
    }

    static Path defaultRoot() {
        String custom = System.getProperty("notes.home");
        return custom != null && !custom.isBlank() ? Path.of(custom) : documents().resolve("Devava Notes");
    }

    List<Notebook> notebooks() throws IOException {
        Files.createDirectories(root);
        List<Path> dirs;
        try (Stream<Path> s = Files.list(root)) {
            dirs = s.filter(d -> Files.isDirectory(d) && !hidden(d)).sorted(BY_NAME).toList();
        }
        List<Notebook> notebooks = new ArrayList<>();
        for (Path dir : dirs) {
            List<Path> notes = notes(dir);
            FileTime edited = Files.getLastModifiedTime(dir);
            for (Path n : notes) {
                FileTime t = Files.getLastModifiedTime(n);
                if (t.compareTo(edited) > 0) edited = t;
            }
            notebooks.add(new Notebook(dir, type(dir), notes.size(), edited));
        }
        return notebooks;
    }

    /** A folder without a readable .notebook.json (made by hand, or by version 0.1) is a General one. */
    static NotebookType type(Path notebook) {
        return NotebookType.byId(meta(notebook, "type"));
    }

    static void setType(Path notebook, NotebookType type) throws IOException {
        setMeta(notebook, "type", type.id());
    }

    /** A text kept in the notebook's .notebook.json ("type", "cpp": its C++ template); "" when not there. */
    static String meta(Path notebook, String key) {
        return jsonText(notebook.resolve(META), key);
    }

    /** Writes one text into .notebook.json, keeping everything else already there. */
    static void setMeta(Path notebook, String key, String value) throws IOException {
        setJsonText(notebook.resolve(META), key, value);
    }

    /** An app setting ("theme"), kept in settings.json next to the notebooks. */
    String setting(String key) {
        return jsonText(root.resolve(SETTINGS), key);
    }

    void setSetting(String key, String value) throws IOException {
        Files.createDirectories(root);
        setJsonText(root.resolve(SETTINGS), key, value);
    }

    private static String jsonText(Path file, String key) {
        JsonElement value = json(file).get(key);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static void setJsonText(Path file, String key, String value) throws IOException {
        JsonObject json = json(file);
        json.addProperty(key, value);
        write(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n");
    }

    private static JsonObject json(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return new JsonObject();   // missing, or not what this app wrote
        }
    }

    /** A note and the notebook it is in. */
    record Place(Path notebook, Path note) {
        String title() {
            return Vault.title(note);
        }
    }

    /** Every note of every notebook: for Quick open, Search and the titles [[ offers. */
    List<Place> everyNote() {
        List<Place> all = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            for (Path dir : s.filter(d -> Files.isDirectory(d) && !hidden(d)).sorted(BY_NAME).toList()) {
                try {
                    for (Path note : notes(dir)) all.add(new Place(dir, note));
                } catch (IOException ignored) {
                    // A notebook that cannot be read right now has nothing to find.
                }
            }
        } catch (IOException ignored) {
            // No notes folder yet: nothing to find.
        }
        return all;
    }

    static List<Path> notes(Path notebook) throws IOException {
        try (Stream<Path> s = Files.list(notebook)) {
            return s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXT))
                    .filter(f -> !hidden(f) && Files.isRegularFile(f))
                    .sorted(BY_NAME).toList();
        }
    }

    Path createNotebook(String name, NotebookType type) throws IOException {
        Files.createDirectories(root);
        Path dir = Files.createDirectory(unique(root, fileName(name), ""));
        setType(dir, type);
        return dir;
    }

    static Path createNote(Path notebook, String title, String text) throws IOException {
        Path note = Files.createFile(unique(notebook, fileName(title), EXT));
        if (!text.isEmpty()) write(note, text);
        return note;
    }

    /** Renames a notebook or a note (keeping its .md). Fails if another one already has that name. */
    static Path rename(Path path, String name) throws IOException {
        Path target = path.resolveSibling(fileName(name) + (Files.isDirectory(path) ? "" : EXT));
        if (target.getFileName().toString().equals(path.getFileName().toString())) return path;
        if (!Files.exists(target)) return Files.move(path, target);
        if (!Files.isSameFile(path, target)) throw new FileAlreadyExistsException(target.getFileName().toString());
        // Only the letter case changes; Windows and macOS see one file, so go through another name.
        Path temp = Files.move(path, path.resolveSibling(".rename-" + System.nanoTime()));
        return Files.move(temp, target);
    }

    /** Copies an image into the notebook's attachments folder (under a new name if taken) and returns the copy. */
    static Path attach(Path notebook, Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path dir = Files.createDirectories(notebook.resolve("attachments"));
        return Files.copy(file, unique(dir, fileName(dot > 0 ? name.substring(0, dot) : name), dot > 0 ? name.substring(dot) : ""));
    }

    /** Saves a pasted image into the notebook's attachments folder as "Pasted image 2026-09-28 15.30.12.png". */
    static Path attach(Path notebook, byte[] png) throws IOException {
        Path dir = Files.createDirectories(notebook.resolve("attachments"));
        String name = "Pasted image " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss"));
        return Files.write(unique(dir, name, ".png"), png);
    }

    static boolean isImage(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|bmp|svg)");
    }

    /** Moves a note or a notebook to the system trash; nothing is ever deleted for good. */
    static void trash(Path path) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            throw new IOException("this system has no trash to move it to, so nothing was deleted");
        }
        if (!Desktop.getDesktop().moveToTrash(path.toFile())) throw new IOException("it could not be moved to the trash");
    }

    static String read(Path note) throws IOException {
        String text = Files.readString(note, StandardCharsets.UTF_8);
        return !text.isEmpty() && text.charAt(0) == 0xFEFF ? text.substring(1) : text;  // BOM left by some Windows editors
    }

    /** Writes atomically, retrying for a moment: OneDrive can hold a file while it syncs it. */
    static void write(Path note, String text) throws IOException {
        Path temp = note.resolveSibling("." + note.getFileName() + ".tmp");
        for (int attempt = 1; ; attempt++) {
            try {
                Files.writeString(temp, text, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, note, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, note, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                if (attempt == 3) {
                    Files.deleteIfExists(temp);
                    throw e;
                }
                try {
                    Thread.sleep(150L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    static String title(Path p) {
        String name = p.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(EXT) ? name.substring(0, name.length() - EXT.length()) : name;
    }

    private static final Pattern RESERVED = Pattern.compile("(?i)con|prn|aux|nul|com[1-9]|lpt[1-9]");

    /**
     * A title turned into a name every system accepts, so notes can move between Windows, macOS
     * and Linux: no \ / : * ? " < > |, no leading dot (hidden) or trailing dot or space, and
     * none of the names Windows reserves (CON, NUL, COM1…), with or without an extension.
     */
    static String fileName(String title) {
        String name = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ")
                .replaceAll("^[. ]+|[. ]+$", "");
        if (name.length() > 80) name = name.substring(0, 80).replaceAll("[. ]+$", "");
        if (name.isEmpty()) return "Untitled";
        return RESERVED.matcher(name.split("\\.", 2)[0].strip()).matches() ? "_" + name : name;
    }

    private static Path unique(Path dir, String base, String ext) {
        Path p = dir.resolve(base + ext);
        for (int i = 2; Files.exists(p); i++) p = dir.resolve(base + " " + i + ext);
        return p;
    }

    private static boolean hidden(Path p) {
        return p.getFileName().toString().startsWith(".");
    }

    private static final Pattern CHUNKS = Pattern.compile("(\\d+)|(\\D+)");

    static int compareNatural(String a, String b) {
        Matcher ma = CHUNKS.matcher(a);
        Matcher mb = CHUNKS.matcher(b);
        while (true) {
            boolean ha = ma.find(), hb = mb.find();
            if (!ha || !hb) return Boolean.compare(ha, hb);
            int cmp = ma.group(1) != null && mb.group(1) != null
                    ? compareNumbers(ma.group(), mb.group())
                    : ma.group().compareToIgnoreCase(mb.group());
            if (cmp != 0) return cmp;
        }
    }

    private static int compareNumbers(String a, String b) {
        String na = a.replaceFirst("^0+(?=\\d)", ""), nb = b.replaceFirst("^0+(?=\\d)", "");
        if (na.length() != nb.length()) return Integer.compare(na.length(), nb.length());
        int cmp = na.compareTo(nb);
        return cmp != 0 ? cmp : Integer.compare(a.length(), b.length());
    }

    /**
     * The real Documents folder: on Windows it may be redirected to OneDrive, and on Linux it is
     * usually named in the desktop's language (~/Documentos), as ~/.config/user-dirs.dirs says.
     */
    private static Path documents() {
        Path home = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            try {
                File d = FileSystemView.getFileSystemView().getDefaultDirectory();
                if (d != null && d.isDirectory()) return d.toPath();
            } catch (RuntimeException | LinkageError ignored) {
                // No Windows shell available: fall back to ~/Documents.
            }
        } else if (!os.contains("mac")) {
            String config = System.getenv("XDG_CONFIG_HOME");
            Path dirs = (config != null && !config.isBlank() ? Path.of(config) : home.resolve(".config")).resolve("user-dirs.dirs");
            Path xdg = xdgDocuments(home, dirs);
            if (xdg != null) return xdg;
        }
        Path documents = home.resolve("Documents");
        return Files.isDirectory(documents) ? documents : home;
    }

    /** The folder of {@code XDG_DOCUMENTS_DIR="$HOME/Documentos"} in user-dirs.dirs, if it exists. */
    private static Path xdgDocuments(Path home, Path dirs) {
        try {
            for (String line : Files.readAllLines(dirs, StandardCharsets.UTF_8)) {
                line = line.strip();
                if (!line.startsWith("XDG_DOCUMENTS_DIR=")) continue;
                Path d = Path.of(line.substring("XDG_DOCUMENTS_DIR=".length()).replace("\"", "")
                        .replace("${HOME}", home.toString()).replace("$HOME", home.toString()));
                return Files.isDirectory(d) && !d.equals(home) ? d : null;
            }
        } catch (IOException | RuntimeException ignored) {
            // No such file, or something odd in it: as if it did not exist.
        }
        return null;
    }
}
