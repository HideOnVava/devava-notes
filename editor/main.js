// The page of Devava Notes' editor: CodeMirror 6 to write a note, and the reading view that Java
// renders with commonmark-java. `npm run build` bundles it into
// src/main/resources/com/devavaxp/notes/editor.js, which is committed.
import {EditorState, RangeSetBuilder} from "@codemirror/state";
import {Decoration, EditorView, ViewPlugin, drawSelection, dropCursor, highlightSpecialChars, keymap, placeholder} from "@codemirror/view";
import {defaultKeymap, history, historyKeymap, indentWithTab} from "@codemirror/commands";
import {highlightSelectionMatches, searchKeymap} from "@codemirror/search";
import {closeBrackets, closeBracketsKeymap} from "@codemirror/autocomplete";
import {LanguageDescription, bracketMatching, indentOnInput, syntaxHighlighting, syntaxTree} from "@codemirror/language";
import {markdown, markdownLanguage} from "@codemirror/lang-markdown";
import {languages} from "@codemirror/language-data";
import {classHighlighter, highlightCode, tagHighlighter, tags} from "@lezer/highlight";

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

const extensions = [
    highlightSpecialChars(), history(), drawSelection(), dropCursor(), indentOnInput(), bracketMatching(),
    closeBrackets(), highlightSelectionMatches(), placeholder("Start writing…"),
    markdown({base: markdownLanguage, codeLanguages: languages}),
    highlighters.map(h => syntaxHighlighting(h)),
    fencedCode, EditorView.lineWrapping,
    keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, indentWithTab]),
    EditorView.updateListener.of(u => {
        if (u.docChanged) tell("changed");
    }),
];

const editor = document.getElementById("editor"), reading = document.getElementById("reading");
const view = new EditorView({parent: editor, state: EditorState.create({extensions})});

// Called by Java.
window.setText = text => view.setState(EditorState.create({doc: text, extensions}));
window.getText = () => view.state.doc.toString();
window.insertText = text => view.dispatch(view.state.replaceSelection(text), {scrollIntoView: true});
window.insertCode = (code, language) => {
    let inCode = false;
    for (let node = syntaxTree(view.state).resolveInner(view.state.selection.main.head, -1); node; node = node.parent) {
        if (node.name === "FencedCode") inCode = true;
    }
    view.dispatch(view.state.replaceSelection(inCode ? code : "\n```" + language + "\n" + code + "\n```\n"), {scrollIntoView: true});
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
    reading.querySelectorAll("pre > code").forEach(card);
    editor.hidden = true;
    reading.hidden = false;
    reading.scrollTop = 0;
};

// A link in the reading view opens in the system browser, never inside this page.
reading.addEventListener("click", e => {
    const link = e.target.closest("a[href]");
    if (!link) return;
    e.preventDefault();
    tell("link:" + link.href);
});

tell("ready");

// A code block of the reading view becomes a card: its language, a Copy button, numbered lines,
// and the same colors as in the editor.
function card(code) {
    const name = code.className.startsWith("language-") ? code.className.slice("language-".length) : "";
    const text = code.textContent.replace(/\n$/, "");
    const head = document.createElement("div"), label = document.createElement("span"), copy = document.createElement("button");
    head.className = "code-head";
    label.textContent = name || "code";
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
    (name ? LanguageDescription.matchLanguageName(languages, name, true) : null)?.load()
        .then(support => lines(code, text, support.language.parser.parse(text)));
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
