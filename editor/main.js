// The page of Devava Notes' editor: CodeMirror 6 to write a note, and the reading view that Java
// renders with commonmark-java. `npm run build` bundles it into
// src/main/resources/com/devavaxp/notes/editor.js, which is committed.
import {EditorState, RangeSetBuilder} from "@codemirror/state";
import {
    Decoration, EditorView, MatchDecorator, ViewPlugin, drawSelection, dropCursor, highlightSpecialChars, keymap, placeholder
} from "@codemirror/view";
import {defaultKeymap, history, historyKeymap, indentWithTab} from "@codemirror/commands";
import {highlightSelectionMatches, searchKeymap} from "@codemirror/search";
import {autocompletion, closeBrackets, closeBracketsKeymap} from "@codemirror/autocomplete";
import {LanguageDescription, bracketMatching, indentOnInput, syntaxHighlighting, syntaxTree} from "@codemirror/language";
import {markdown, markdownLanguage} from "@codemirror/lang-markdown";
import {languages} from "@codemirror/language-data";
import {classHighlighter, highlightCode, tagHighlighter, tags} from "@lezer/highlight";
import katex from "katex";

// Java never hands this page an object: the page tells Java things as text, through alert().
const tell = message => window.alert(message);
window.onerror = (message, source, line) => tell(`error:${message} (line ${line})`);
window.addEventListener("unhandledrejection", e => tell(`error:${e.reason}`));

// classHighlighter's tok-* classes plus the tags it leaves out; editor.css colors them all.
const highlighters = [classHighlighter, tagHighlighter([
    {tag: tags.processingInstruction, class: "tok-mark"},   // Markdown marks (# ** ```) and #include
    {tag: tags.heading1, class: "tok-h1"},
    {tag: tags.heading2, class: "tok-h2"},
    {tag: tags.heading3, class: "tok-h3"},
    {tag: tags.monospace, class: "tok-monospace"},          // `inline code`
    {tag: tags.strikethrough, class: "tok-strike"},
    {tag: tags.quote, class: "tok-quote"},
])];

/** Whether a position is inside code (a fenced block or `inline code`), where [[links]] and #tags are just text. */
function inCode(state, pos) {
    for (let node = syntaxTree(state).resolveInner(pos, 1); node; node = node.parent) {
        if (node.name === "FencedCode" || node.name === "InlineCode" || node.name === "CodeBlock") return true;
    }
    return false;
}

// The lines of a fenced code block get the code font, as in the reading view.
const codeLine = Decoration.line({class: "cm-code"});

function codeLines(state) {
    const lines = new RangeSetBuilder();
    let last = -1;
    syntaxTree(state).iterate({
        enter: node => {
            if (node.name !== "FencedCode") return;
            for (let pos = node.from; pos <= node.to;) {
                const line = state.doc.lineAt(pos);
                if (line.from > last) lines.add(line.from, line.from, codeLine);
                last = line.from;
                pos = line.to + 1;
            }
            return false;
        }
    });
    return lines.finish();
}

const fencedCode = ViewPlugin.fromClass(class {
    constructor(view) {
        this.decorations = codeLines(view.state);
    }

    update(u) {
        if (u.docChanged || syntaxTree(u.startState) !== syntaxTree(u.state)) this.decorations = codeLines(u.state);
    }
}, {decorations: plugin => plugin.decorations});

// [[links]] and #tags stand out while writing; a #tag needs a letter, as in Obsidian (#1 is not one).
const TAG = /(?<![\p{L}\p{N}_\/#&])#[\p{L}\p{N}_][\p{L}\p{N}_\/-]*/gu;
const LINK = /\[\[([^\]\n|]+)(?:\|[^\]\n]*)?\]\]/g;

function marking(regexp, className, keep = () => true) {
    const mark = Decoration.mark({class: className});
    const matcher = new MatchDecorator({
        regexp,
        decorate: (add, from, to, match, view) => {
            if (keep(match[0]) && !inCode(view.state, from)) add(from, to, mark);
        }
    });
    return ViewPlugin.fromClass(class {
        constructor(view) {
            this.decorations = matcher.createDeco(view);
        }

        update(u) {
            this.decorations = matcher.updateDeco(u, this.decorations);
        }
    }, {decorations: plugin => plugin.decorations});
}

// Typing [[ offers the titles of the notes (Java sends them); Ctrl+click on a [[link]] opens it.
let titles = [];

function linkCompletions(context) {
    const typed = context.matchBefore(/\[\[[^\]\n]*$/);
    if (!typed || inCode(context.state, typed.from)) return null;
    return {
        from: typed.from + 2,
        validFor: /^[^\]\n]*$/,
        options: titles.map(title => ({
            label: title,
            apply: (view, completion, from, to) => {
                const closing = view.state.sliceDoc(to, to + 2) === "]]" ? "" : "]]";
                view.dispatch({changes: {from, to, insert: title + closing}, selection: {anchor: from + title.length + 2}});
            }
        }))
    };
}

const followLinks = EditorView.domEventHandlers({
    mousedown(e, view) {
        if (!e.ctrlKey && !e.metaKey) return false;
        const pos = view.posAtCoords({x: e.clientX, y: e.clientY});
        if (pos == null) return false;
        const line = view.state.doc.lineAt(pos);
        for (const m of line.text.matchAll(LINK)) {
            const from = line.from + m.index;
            if (pos >= from && pos <= from + m[0].length) {
                e.preventDefault();
                tell("open:" + m[1].trim());
                return true;
            }
        }
        return false;
    }
});

// Copy and cut go through Java: when a page writes to the clipboard (as CodeMirror's own copy does),
// JavaFX's WebKit empties it instead. Java gets the text CodeMirror would copy: the selections, or the
// cursors' whole lines when nothing is selected. A paste with no text (a screenshot, image files) asks
// Java too, which saves the pictures and puts them in the note.
function copy(e, view) {
    const {state} = view, selected = state.selection.ranges.filter(r => !r.empty);
    const ranges = selected.length ? selected : [...new Set(state.selection.ranges.map(r => state.doc.lineAt(r.head).number))]
        .map(n => state.doc.line(n)).map(line => ({from: line.from, to: Math.min(line.to + 1, state.doc.length)}));
    tell("copy:" + ranges.map(r => state.sliceDoc(r.from, r.to)).join(selected.length ? state.lineBreak : ""));
    if (e.type === "cut") view.dispatch({changes: ranges, scrollIntoView: true, userEvent: "delete.cut"});
    e.preventDefault();
    return true;
}

const clipboard = EditorView.domEventHandlers({
    copy,
    cut: copy,
    paste(e) {
        if (e.clipboardData?.getData("text/plain")) return false;   // text: CodeMirror pastes it
        e.preventDefault();
        tell("paste");
        return true;
    }
});

const extensions = [
    highlightSpecialChars(), history(), drawSelection(), dropCursor(), indentOnInput(), bracketMatching(),
    closeBrackets(), highlightSelectionMatches(), placeholder("Start writing…"),
    markdown({base: markdownLanguage, codeLanguages: languages}),
    highlighters.map(h => syntaxHighlighting(h)),
    fencedCode, marking(LINK, "cm-wikilink"), marking(TAG, "cm-tag", text => /\p{L}/u.test(text)), followLinks, clipboard,
    autocompletion({override: [linkCompletions], icons: false}),
    EditorView.lineWrapping,
    keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, indentWithTab]),
    EditorView.updateListener.of(u => {
        if (u.docChanged) tell("changed");
    }),
];

const editor = document.getElementById("editor"), reading = document.getElementById("reading");
const view = new EditorView({parent: editor, state: EditorState.create({extensions})});

/** Accents and case do not matter when searching, and a folded text keeps the positions of the original. */
function fold(text) {
    let out = "";
    for (let i = 0; i < text.length; i++) out += text[i].normalize("NFD").replace(/\p{M}/gu, "").toLowerCase()[0] ?? text[i];
    return out;
}

// Called by Java.
window.setText = text => view.setState(EditorState.create({doc: text, extensions}));
window.getText = () => view.state.doc.toString();
window.setTitles = lines => titles = lines ? lines.split("\n") : [];
window.setTheme = theme => document.documentElement.dataset.theme = theme;
window.insertText = text => view.dispatch(view.state.replaceSelection(text), {scrollIntoView: true});
window.insertCode = (code, language) => {
    let inBlock = false;
    for (let node = syntaxTree(view.state).resolveInner(view.state.selection.main.head, -1); node; node = node.parent) {
        if (node.name === "FencedCode") inBlock = true;
    }
    const from = view.state.selection.main.from;
    view.dispatch(view.state.replaceSelection(inBlock ? code : "\n```" + language + "\n" + code + "\n```\n"), {scrollIntoView: true});
    if (!inBlock && !code) view.dispatch({selection: {anchor: from + language.length + 5}});   // into the new, empty block
    view.focus();
};
window.selectMatch = text => {
    const at = fold(view.state.doc.toString()).indexOf(fold(text));
    if (at < 0) return;
    view.dispatch({selection: {anchor: at, head: at + text.length}, scrollIntoView: true});
    view.focus();
};
window.focusEditor = () => view.focus();
window.showEditor = () => {
    reading.hidden = true;
    editor.hidden = false;
    view.focus();
};
window.showReading = html => {
    reading.innerHTML = html;   // commonmark-java already turned any HTML written in the note into text
    reading.querySelectorAll(".math").forEach(math => katex.render(math.textContent, math,
        {displayMode: math.classList.contains("display"), throwOnError: false}));
    reading.querySelectorAll("pre > code").forEach(card);
    editor.hidden = true;
    reading.hidden = false;
    reading.scrollTop = 0;
};

// In the reading view a [[link]] opens its note, a #tag shows its notes, and any other link opens
// in the system browser, never inside this page.
reading.addEventListener("click", e => {
    const note = e.target.closest(".wikilink"), tag = e.target.closest(".tag"), link = e.target.closest("a[href]");
    if (note) tell("open:" + note.dataset.note);
    else if (tag) tell("tag:" + tag.dataset.tag);
    else if (link) tell("link:" + link.href);
    if (note || link) e.preventDefault();
});

tell("ready");

// A code block of the reading view becomes a card: its language, a Copy button, numbered lines,
// and the same colors as in the editor.
function card(code) {
    const name = code.className.startsWith("language-") ? code.className.slice("language-".length) : "";
    const language = name ? LanguageDescription.matchLanguageName(languages, name, true) : null;
    const text = code.textContent.replace(/\n$/, "");
    const head = document.createElement("div"), label = document.createElement("span"), copy = document.createElement("button");
    head.className = "code-head";
    label.textContent = language?.name ?? (name || "code");   // "C++" for cpp, "PLSQL" for plsql
    copy.className = "copy";
    copy.textContent = "Copy";
    copy.addEventListener("click", () => {
        tell("copy:" + text);
        copy.textContent = "Copied";
        setTimeout(() => copy.textContent = "Copy", 1500);
    });
    head.append(label, copy);
    const box = document.createElement("div"), pre = code.parentElement;
    box.className = "code-card";
    pre.replaceWith(box);
    box.append(head, pre);
    lines(code, text, null);
    language?.load().then(support => lines(code, text, support.language.parser.parse(text)));
}

function lines(code, text, tree) {
    const out = document.createDocumentFragment();
    let line;
    const next = () => {
        line = document.createElement("span");
        line.className = "line";
        out.append(line);
    };
    next();
    const put = (piece, classes) => line.append(classes
        ? Object.assign(document.createElement("span"), {className: classes, textContent: piece})
        : piece);
    if (tree) {
        highlightCode(text, tree, highlighters, put, () => {
            out.append("\n");
            next();
        });
    } else {
        text.split("\n").forEach((piece, i) => {
            if (i > 0) {
                out.append("\n");
                next();
            }
            put(piece, "");
        });
    }
    code.replaceChildren(out);
}
