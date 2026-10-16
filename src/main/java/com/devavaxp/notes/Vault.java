package com.devavaxp.notes;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.filechooser.FileSystemView;
import java.awt.Desktop;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The notes on disk: under Documents/Devava Notes, one folder per notebook and one Markdown
 * file per note, whose file name is its title; the notebook's type is in its .notebook.json.
 * {@code -Dnotes.home=path} moves it all elsewhere. A note is written to a temporary file that
 * then replaces it, so a crash never leaves it cut.
 */
final class Vault {

    static final String EXT = ".md";
    private static final String META = ".notebook.json", SETTINGS = "settings.json";

    /** A notebook as the home screen lists it, with its school period: the one set, or that of its last edit. */
    record Notebook(Path dir, NotebookType type, int notes, FileTime edited, Period period) {
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
            Period period = Period.parse(meta(dir, "period"));
            if (period == null) period = Period.of(LocalDate.ofInstant(edited.toInstant(), ZoneId.systemDefault()));
            notebooks.add(new Notebook(dir, type(dir), notes.size(), edited, period));
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

    /**
     * The note a [[title]] written in a notebook leads to: that notebook's own note with that title
     * (in any letter case), else the first one of all; null when there is none.
     */
    static Path linkTarget(List<Place> places, String title, Path notebook) {
        Path first = null;
        for (Place p : places) {
            if (!p.title().equalsIgnoreCase(title)) continue;
            if (p.notebook().equals(notebook)) return p.note();
            if (first == null) first = p.note();
        }
        return first;
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

    /**
     * A note's text with its [[links]] to one title (in any letter case, with or without |words to
     * show) leading to another title instead. In code they are no links, as the reading view has it
     * (Editor.setAside).
     */
    static String relink(String text, String from, String to) {
        return relink(text, title -> title.equalsIgnoreCase(from) ? to : null);
    }

    /** The titles a note's text links to ([[title]], [[title|words]]), outside code, as written. */
    static List<String> links(String text) {
        List<String> titles = new ArrayList<>();
        relink(text, title -> {
            titles.add(title);
            return null;
        });
        return titles;
    }

    /**
     * The notes whose [[links]] lead to {@code note} (as linkTarget leads them, from their own
     * notebook), each one's links as {@code linksOf} gives them (links of its text); those of its
     * notebook first.
     */
    static List<Place> linkingTo(List<Place> places, Path note, Function<Path, List<String>> linksOf) {
        String title = title(note);
        List<Place> found = new ArrayList<>();
        for (Place p : places) {
            if (p.note().equals(note)) continue;
            for (String t : linksOf.apply(p.note())) {
                if (t.equalsIgnoreCase(title) && note.equals(linkTarget(places, t, p.notebook()))) {
                    found.add(p);
                    break;
                }
            }
        }
        Path home = note.getParent();
        found.sort(Comparator.comparing(p -> !p.notebook().equals(home)));
        return found;
    }

    /** A text with each [[link]] outside code given to {@code change}: the title to lead to instead, or null to leave it. */
    private static String relink(String text, Function<String, String> change) {
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
            int end = i + 1;
            if (text.charAt(i) == '\\') {
                end = Math.min(i + 2, n);
            } else if (text.charAt(i) == '`') {   // an inline code span, whole
                int run = 0;
                while (i + run < n && text.charAt(i + run) == '`') run++;
                int close = text.indexOf("`".repeat(run), i + run);
                end = close < 0 ? i + run : close + run;
            } else if (text.startsWith("[[", i)) {
                int close = text.indexOf("]]", i + 2), line = text.indexOf('\n', i);
                String[] parts = close > i + 2 && (line < 0 || close < line) ? text.substring(i + 2, close).split("\\|", 2) : null;
                String other = parts == null || parts[0].isBlank() ? null : change.apply(parts[0].strip());
                if (other != null) {
                    out.append("[[").append(other).append(parts.length > 1 ? "|" + parts[1] : "").append("]]");
                    i = close + 2;
                    continue;
                }
            }
            out.append(text, i, end);
            i = end;
        }
        return out.toString();
    }

    /** Copies an image into the notebook's attachments folder (under a new name if taken) and returns the copy. */
    static Path attach(Path notebook, Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path dir = Files.createDirectories(notebook.resolve("attachments"));
        return Files.copy(file, unique(dir, fileName(dot > 0 ? name.substring(0, dot) : name), dot > 0 ? name.substring(dot) : ""));
    }

    /** Saves a pasted image into the notebook's attachments folder as "Imagen pegada 2026-09-28 15.30.12.png". */
    static Path attach(Path notebook, byte[] png) throws IOException {
        Path dir = Files.createDirectories(notebook.resolve("attachments"));
        String name = "Imagen pegada " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss"));
        return Files.write(unique(dir, name, ".png"), png);
    }

    /**
     * Saves a new drawing into the notebook's attachments folder as "Dibujo 2026-10-14 15.30.12.excalidraw.png":
     * Excalidraw's name for a PNG that holds the drawing itself, so that it can be drawn on again.
     */
    static Path saveDrawing(Path notebook, byte[] png) throws IOException {
        Path dir = Files.createDirectories(notebook.resolve("attachments"));
        String name = "Dibujo " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss"));
        Path file = unique(dir, name, ".excalidraw.png");
        write(file, png);
        return file;
    }

    /** The drawings' personal library (Excalidraw's), which every drawing window shares. */
    Path library() {
        return root.resolve("Biblioteca de dibujos.excalidrawlib");
    }

    /** Whether a picture is a drawing made in the app, which it can open to draw on again. */
    static boolean isDrawing(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".excalidraw.png");
    }

    /**
     * Whether a drawing holds pictures (a screenshot, a photo), which the dark theme leaves as they are
     * instead of inverting them with the drawing. Excalidraw keeps the scene in a tEXt chunk of the PNG,
     * as JSON whose "encoded" text is the scene deflated, one byte per character; its files are its pictures.
     */
    static boolean hasPictures(Path drawing) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(drawing)))) {
            in.skipNBytes(8);   // the PNG signature
            while (true) {
                int length = in.readInt();
                String type = new String(in.readNBytes(4), StandardCharsets.ISO_8859_1);
                if (type.equals("IEND")) return false;
                if (!type.equals("tEXt")) {
                    in.skipNBytes(length + 4L);   // its data and CRC
                    continue;
                }
                String text = new String(in.readNBytes(length), StandardCharsets.ISO_8859_1);
                in.skipNBytes(4);
                if (!text.startsWith(SCENE)) continue;
                JsonObject meta = JsonParser.parseString(text.substring(SCENE.length())).getAsJsonObject();
                byte[] scene = meta.get("encoded").getAsString().getBytes(StandardCharsets.ISO_8859_1);
                if (meta.get("compressed").getAsBoolean()) scene = new InflaterInputStream(new ByteArrayInputStream(scene)).readAllBytes();
                return !JsonParser.parseString(new String(scene, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("files").isEmpty();
            }
        } catch (IOException | RuntimeException e) {
            return false;   // no scene to read: shown as any other drawing
        }
    }

    private static final String SCENE = "application/vnd.excalidraw+json\0";

    static boolean isImage(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|bmp|svg)");
    }

    /** Moves a note or a notebook to the system trash; nothing is ever deleted for good. */
    static void trash(Path path) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            throw new IOException("este sistema no tiene papelera, así que no se borró nada");
        }
        if (!Desktop.getDesktop().moveToTrash(path.toFile())) throw new IOException("no se pudo mover a la papelera");
    }

    /** Packs a notebook (its notes, pictures and .notebook.json) into a ZIP, in a folder named after it: to share it. */
    static void exportZip(Path notebook, Path zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip)); Stream<Path> files = Files.walk(notebook)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new ZipEntry(notebook.getFileName() + "/" + notebook.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }

    /**
     * Unpacks a notebook shared as a ZIP (exportZip's, or its folder zipped by hand) into the notes
     * folder, under another name if its own is taken, and returns its folder. Nothing in the ZIP may
     * land outside that folder, and if anything fails no half of the notebook stays.
     */
    Path importZip(Path zip) throws IOException {
        ZipFile file;
        try {
            file = new ZipFile(zip.toFile());
        } catch (ZipException e) {
            throw new IOException("no es un archivo ZIP", e);
        }
        try (file) {
            List<? extends ZipEntry> entries = file.stream().filter(e -> !e.isDirectory()).toList();
            if (entries.isEmpty()) throw new IOException("el ZIP está vacío");
            String first = entries.get(0).getName().replace('\\', '/');
            String top = first.indexOf('/') > 0 ? first.substring(0, first.indexOf('/') + 1) : "";
            boolean inFolder = !top.isEmpty() && entries.stream().allMatch(e -> e.getName().replace('\\', '/').startsWith(top));
            String name = inFolder ? top.substring(0, top.length() - 1) : zip.getFileName().toString().replaceFirst("(?i)\\.zip$", "");
            Files.createDirectories(root);
            Path dir = Files.createDirectory(unique(root, fileName(name), ""));
            try {
                for (ZipEntry entry : entries) {
                    String path = entry.getName().replace('\\', '/');
                    Path target = dir.resolve(inFolder ? path.substring(top.length()) : path).normalize();
                    if (!target.startsWith(dir) || target.equals(dir)) {
                        throw new IOException(String.format("“%s” apunta fuera de su carpeta", entry.getName()));
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream in = file.getInputStream(entry)) {
                        Files.copy(in, target);
                    }
                }
            } catch (IOException | RuntimeException e) {
                try (Stream<Path> made = Files.walk(dir)) {
                    for (Path p : made.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                }
                throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
            }
            return dir;
        }
    }

    static String read(Path note) throws IOException {
        String text = Files.readString(note, StandardCharsets.UTF_8);
        return !text.isEmpty() && text.charAt(0) == 0xFEFF ? text.substring(1) : text;  // BOM left by some Windows editors
    }

    /** Writes atomically, retrying for a moment: OneDrive can hold a file while it syncs it. */
    static void write(Path note, String text) throws IOException {
        write(note, text.getBytes(StandardCharsets.UTF_8));
    }

    static void write(Path file, byte[] bytes) throws IOException {
        Path temp = file.resolveSibling("." + file.getFileName() + ".tmp");
        for (int attempt = 1; ; attempt++) {
            try {
                Files.write(temp, bytes);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
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
        if (name.isEmpty()) return "Sin título";
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
