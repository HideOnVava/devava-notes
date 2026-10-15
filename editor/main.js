// The page of Devava Notes' editor: CodeMirror 6 to write a note, and the reading view that Java
// renders with commonmark-java. `npm run build` bundles it into
// src/main/resources/com/devavaxp/notes/editor.js, which is committed.
import {EditorSelection, EditorState, Prec, RangeSetBuilder, StateField, Transaction} from "@codemirror/state";
import {
    Decoration, EditorView, MatchDecorator, ViewPlugin, WidgetType, drawSelection, dropCursor, highlightSpecialChars, keymap, placeholder
} from "@codemirror/view";
import {defaultKeymap, history, historyKeymap, indentWithTab} from "@codemirror/commands";
import {highlightSelectionMatches, searchKeymap} from "@codemirror/search";
import {autocompletion, closeBrackets, closeBracketsKeymap, startCompletion} from "@codemirror/autocomplete";
import {LanguageDescription, bracketMatching, indentOnInput, syntaxHighlighting, syntaxTree} from "@codemirror/language";
import {markdown, markdownLanguage} from "@codemirror/lang-markdown";
import {languages} from "@codemirror/language-data";
import {classHighlighter, highlightCode, tagHighlighter, tags} from "@lezer/highlight";
import katex from "katex";

// Java never hands this page an object: the page tells Java things as text, through alert().
const tell = message => window.alert(message);
window.onerror = (message, source, line) => tell(`error:${message} (line ${line})`);
window.addEventListener("unhandledrejection", e => tell(`error:${e.reason}`));

// The page's words, and CodeMirror's own (its search panel), in Spanish.
const words = {
    placeholder: "Empieza a escribir…", copy: "Copiar", copied: "Copiado", remove: "Quitar", sure: "¿Quitar?",
    code: "Código", choose: "Elegir lenguaje", change: "Cambiar el lenguaje del bloque", output: "Salida",
    outputLong: "Salida de consola", more: "Más lenguajes…", find: "Buscar un lenguaje", use: "Usar", current: "actual",
    block: "Bloque de código",
};
const phrases = {
    "Find": "Buscar", "Replace": "Reemplazar", "next": "siguiente", "previous": "anterior", "all": "todas",
    "match case": "mayúsculas", "by word": "palabra completa", "regexp": "expresión regular", "replace": "reemplazar",
    "replace all": "reemplazar todas", "close": "cerrar", "Go to line": "Ir a la línea", "go": "ir",
};

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

// ---------- The languages of code blocks ----------

// The languages a code block offers first, as written after ``` (so other apps read them too) and as
// shown, with their usual colors; the rest of language-data is under "Más lenguajes…". A block of
// `output` holds what a program printed. SQL Server's T-SQL is written tsql.
const FIRST = [
    {id: "java", name: "Java", color: "#E76F00"},
    {id: "cpp", name: "C++", color: "#00599C"},
    {id: "c", name: "C", color: "#6E7588"},
    {id: "python", name: "Python", color: "#3776AB"},
    {id: "csharp", name: "C#", color: "#8A3FB8"},
    {id: "javascript", name: "JavaScript", color: "#D4A900"},
    {id: "sql", name: "SQL", color: "#0377B5"},
    {id: "plsql", name: "PL/SQL", color: "#C74634", hint: "Oracle"},
].map(l => ({...l, data: LanguageDescription.matchLanguageName(languages, l.id, false)}));
const OUTPUT = "output";
const ALSO = {tsql: "MS SQL", mssql: "MS SQL", mariadb: "MariaDB SQL", asm: "Gas", masm: "Gas", nasm: "Gas"};
// language-data's names the app shows otherwise, as Java does (Editor.languageName).
const NAMES = {"Gas": "Ensamblador", "Shell": "Bash", "MS SQL": "T-SQL"};

/** The language word of a block's info: "java" in ```java. */
const idIn = info => info.trim().split(/\s/)[0];

/** What CodeMirror colors a block with: its language ("java", "c++", "tsql"…), if it knows it. */
function language(info) {
    const id = idIn(info).toLowerCase();
    return !id || id === OUTPUT ? null : LanguageDescription.matchLanguageName(languages, ALSO[id] ?? id, true);
}

/** How a block shows: its language's name and color, Salida, or an invitation to choose a language. */
function describe(info) {
    const id = idIn(info);
    if (!id) return {id, name: words.choose, unset: true};
    if (id.toLowerCase() === OUTPUT) return {id: OUTPUT, name: words.output, output: true};
    const data = language(info);
    return FIRST.find(l => data && l.data === data) ?? {id, name: NAMES[data?.name] ?? data?.name ?? id};
}

/** What to write after ``` for a language of language-data: a word CodeMirror reads back as that language. */
function idOf(l) {
    return [l.name.toLowerCase(), ...l.alias, ...l.extensions]
            .find(c => !/\s/.test(c) && LanguageDescription.matchLanguageName(languages, c, false) === l)
        ?? Object.keys(ALSO).find(k => ALSO[k] === l.name);
}

/** The language of new blocks in the open notebook (Java says which); "" asks for one. */
let codeLanguage = "";

// ---------- Bits of the page ----------

// The app's line icons (Icons.java) that the page needs.
const ICONS = {
    copy: "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3",
    trash: "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3",
    down: "M6 9l6 6 6-6",
    plus: "M12 5v14M5 12h14",
    terminal: "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M7 9l3 3-3 3 M12 15h5",
};

function icon(name) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    const path = document.createElementNS(svg.namespaceURI, "path");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("class", "icon");
    path.setAttribute("d", ICONS[name]);
    svg.append(path);
    return svg;
}

function element(tag, className, ...children) {
    const e = document.createElement(tag);
    e.className = className;
    e.append(...children);
    return e;
}

/** A language's color, as a dot. */
function dot(color) {
    const d = element("span", "dot");
    if (color) d.style.background = color;
    return d;
}

/** A button of the page; its words go in a .label, and pressing it leaves the focus where it was. */
function button(className, ...children) {
    const b = element("button", className, ...children.map(c => typeof c === "string" ? element("span", "label", c) : c));
    b.type = "button";
    b.addEventListener("mousedown", e => e.preventDefault());
    return b;
}

/** A button says something for a moment ("Copiado"), then what it said before. */
function say(b, now, then) {
    const label = b.querySelector(".label") ?? b;
    label.textContent = now;
    setTimeout(() => label.textContent = then, 1500);
}

// ---------- Code blocks as cards ----------

/** The code of a card: its lines between the fences. */
const codeOf = (state, card) => state.sliceDoc(card.open.to + 1, card.close.from - 1);

// CodeMirror measures a block widget by its box, margins left out, and maps clicks and arrow keys
// with those measures: the space around a card is padding inside its widgets. A click on a card
// that is not on one of its buttons is the editor's, so the cursor goes where it lands.
const onButton = e => e.target instanceof Element && e.target.closest("button") != null;

/** The top of a card: its language (a menu to change it), Copiar and Quitar; a line above it when the cursor is right before it. */
class Head extends WidgetType {
    constructor(info, here) {
        super();
        this.info = info;
        this.here = here;
    }

    eq(other) {
        return other.info === this.info && other.here === this.here;
    }

    toDOM(view) {
        const kind = describe(this.info), head = element("div", "cm-card-head");
        const chip = button("chip" + (kind.unset ? " unset" : ""), kind.output ? icon("terminal") : dot(kind.color), kind.name, icon("down"));
        chip.title = words.change;
        chip.addEventListener("click", () => menu?.anchor === chip ? closeMenu() : openMenu(view, chip, cardOf(view, head)));
        const copy = button("act", icon("copy"), words.copy), remove = button("act", icon("trash"), words.remove);
        copy.addEventListener("click", () => {
            const card = cardOf(view, head);
            if (!card) return;
            tell("copy:" + codeOf(view.state, card));
            say(copy, words.copied, words.copy);
        });
        remove.addEventListener("click", () => {
            const card = cardOf(view, head);
            if (!card) return;
            if (codeOf(view.state, card).trim() && !remove.classList.contains("sure")) {   // with code in it, a second click
                remove.classList.add("sure");
                remove.querySelector(".label").textContent = words.sure;
                setTimeout(() => {
                    remove.classList.remove("sure");
                    remove.querySelector(".label").textContent = words.remove;
                }, 3000);
                return;
            }
            view.dispatch(removal(view.state, card));
            view.focus();
        });
        head.append(chip, element("span", "grow"), copy, remove);
        return element("div", "cm-card-top" + (this.here ? " here" : ""), head);
    }

    get estimatedHeight() {
        return 46;
    }

    ignoreEvent(e) {
        return onButton(e);
    }
}

/** The bottom edge of a card; a line below it when the cursor is right after it. */
class Foot extends WidgetType {
    constructor(here) {
        super();
        this.here = here;
    }

    eq(other) {
        return other.here === this.here;
    }

    toDOM() {
        return element("div", "cm-card-bottom" + (this.here ? " here" : ""), element("div", "cm-card-foot"));
    }

    get estimatedHeight() {
        return 19;
    }

    ignoreEvent() {
        return false;
    }
}

/** "+ Bloque de código" at the end of a note that has code blocks. */
class AddBlock extends WidgetType {
    eq() {
        return true;
    }

    toDOM(view) {
        const add = button("cm-add-block", icon("plus"), words.block);
        add.addEventListener("click", () => newBlock(view, view.state.doc.length));
        return element("div", "cm-add-row", add);
    }

    get estimatedHeight() {
        return 54;
    }

    ignoreEvent(e) {
        return onButton(e);
    }
}

const codeLine = Decoration.line({class: "cm-code"}), outputLine = Decoration.line({class: "cm-code cm-output"});
const fence = Decoration.mark({});

/**
 * The fenced code blocks of the note. One at the top level, closed and with a line of code at
 * least, shows as a card: its ``` lines give way to a head and a foot, which the cursor steps over,
 * and its lines are numbered. Any other (still being written, or inside a list) gets the code font.
 */
function scan(state) {
    const {doc} = state, list = [], decorations = new RangeSetBuilder(), atomic = new RangeSetBuilder();
    const cursor = state.selection.main.empty ? state.selection.main.head : -1;
    syntaxTree(state).iterate({
        enter: node => {
            if (node.name !== "FencedCode") return;
            const open = doc.lineAt(node.from), close = doc.lineAt(node.to), info = node.node.getChild("CodeInfo");
            if (node.node.parent?.name !== "Document" || node.node.getChildren("CodeMark").length < 2 || close.number - open.number < 2) {
                for (let n = open.number; n <= close.number; n++) decorations.add(doc.line(n).from, doc.line(n).from, codeLine);
                return false;
            }
            const card = {open, close, info: info ? doc.sliceString(info.from, info.to) : ""}, output = describe(card.info).output;
            list.push(card);
            decorations.add(open.from, open.to, Decoration.replace({widget: new Head(card.info, cursor === open.from), block: true}));
            for (let n = open.number + 1; n < close.number; n++) {
                decorations.add(doc.line(n).from, doc.line(n).from, output ? outputLine
                    : Decoration.line({class: "cm-code cm-card-line", attributes: {"data-n": String(n - open.number)}}));
            }
            decorations.add(close.from, close.to, Decoration.replace({widget: new Foot(cursor === close.to), block: true}));
            atomic.add(open.from, open.to + 1, fence);   // a fence and the line break between it and the code
            atomic.add(close.from - 1, close.to, fence);
            return false;
        }
    });
    if (list.length) decorations.add(doc.length, doc.length, Decoration.widget({widget: new AddBlock(), block: true, side: 1}));
    return {list, decorations: decorations.finish(), atomic: atomic.finish()};
}

const cards = StateField.define({
    create: scan,
    update: (value, tr) => tr.docChanged || tr.selection || syntaxTree(tr.state) !== syntaxTree(tr.startState) ? scan(tr.state) : value,
    provide: field => [
        EditorView.decorations.from(field, value => value.decorations),
        EditorView.atomicRanges.of(view => view.state.field(field).atomic),
        // Right before or after a card, the line above or below it shows where typing goes, not the caret.
        EditorView.editorAttributes.compute([field, "selection"], state => {
            const {empty, head} = state.selection.main;
            return {class: empty && state.field(field).list.some(c => head === c.open.from || head === c.close.to) ? "cm-beside-card" : ""};
        }),
    ],
});

// ---------- Headings and lists, lined up ----------

// A heading's #s hang in the margin, so its text starts where the text around it does. A list's
// levels step in by 1.5em, and what wraps of an item, or continues it on the lines below, starts
// where its text does.
const headingMarks = Decoration.mark({class: "cm-hmark"});

/** The innermost list item around a line, how deep it is, and where its text starts (in em). */
function listItem(state, line) {
    const indent = /^[ \t]*/.exec(line.text)[0].length;
    if (indent === line.text.length) return null;
    let item = null, depth = 0;
    for (let n = syntaxTree(state).resolveInner(line.from + indent, 1); n; n = n.parent) {
        if (n.name === "FencedCode" || n.name === "CodeBlock") return null;
        if (n.name === "ListItem") {
            item ??= n;
            depth++;
        }
    }
    if (!item) return null;
    const task = item.getChild("Task")?.getChild("TaskMarker");
    const column = depth * 1.5 + (item.parent?.name === "OrderedList" ? 0.7 : 0) + (task ? 1.4 : 0);
    return {item, task, indent, column};
}

function layout(view) {
    const {state} = view, builder = new RangeSetBuilder();
    let done = 0;   // the last line decorated: a line can sit in two visible ranges
    for (const {from, to} of view.visibleRanges) {
        for (let pos = from; pos <= to;) {
            const line = state.doc.lineAt(pos);
            pos = line.to + 1;
            if (line.number <= done) continue;
            done = line.number;
            const heading = /^#{1,6}[ \t]+/.exec(line.text);
            if (heading && syntaxTree(state).resolveInner(line.from, 1).parent?.name.startsWith("ATXHeading")) {
                builder.add(line.from, line.from + heading[0].length, headingMarks);
                continue;
            }
            const list = listItem(state, line);
            if (!list) continue;
            const em = list.column + "em";
            const first = state.doc.lineAt(list.item.from).number === line.number;
            // the item's mark (with its task box), or the indent of a line that continues it
            let end = line.from + list.indent;
            if (first) {
                end = (list.task && state.doc.lineAt(list.task.from).number === line.number ? list.task : list.item.firstChild).to;
                while (end < line.to && /[ \t]/.test(state.sliceDoc(end, end + 1))) end++;
            }
            builder.add(line.from, line.from, Decoration.line({
                attributes: {style: `padding-left: calc(6px + ${em}); text-indent: ${end > line.from ? `-${em}` : "0"}`}}));
            if (end > line.from) builder.add(line.from, end, Decoration.mark({class: "cm-lprefix", attributes: {style: `min-width: ${em}`}}));
        }
    }
    return builder.finish();
}

const lineUp = ViewPlugin.fromClass(class {
    constructor(view) {
        this.decorations = layout(view);
    }

    update(u) {
        if (u.docChanged || u.viewportChanged || syntaxTree(u.startState) !== syntaxTree(u.state)) this.decorations = layout(u.view);
    }
}, {decorations: plugin => plugin.decorations});

/** The card a head element of the page belongs to, as the note is now. */
function cardOf(view, head) {
    const at = view.posAtDOM(head);
    return view.state.field(cards).list.find(c => c.open.from <= at && at <= c.open.to);
}

/** Removing a card, with the line break after it (or before it, at the end of the note). */
function removal(state, card) {
    let from = card.open.from, to = card.close.to;
    if (to < state.doc.length) to++;
    else if (from > 0) from--;
    return {changes: {from, to}, selection: {anchor: from}, scrollIntoView: true, userEvent: "card.remove"};
}

/** Whether a change leaves a card's fences anything but whole and as they were, unless it removes the card whole. */
function breaks(tr, card) {
    let whole = false;
    tr.changes.iterChangedRanges((fromA, toA) => whole ||= fromA <= card.open.from && toA >= card.close.to);
    if (whole) return false;
    const doc = tr.newDoc, open = tr.changes.mapPos(card.open.from, 1), close = tr.changes.mapPos(card.close.to, -1);
    const first = doc.lineAt(open), last = doc.lineAt(close);
    return first.from !== open || first.text !== card.open.text || last.to !== close || last.text !== card.close.text
        || last.number - first.number < 2;
}

/**
 * The ``` lines of a card are out of sight, so typing and deleting next to them must leave them as
 * they are: what is typed right before or after a card goes on a line of its own, and a Backspace or
 * Delete that would eat a fence moves into the card instead, or removes it when it holds no code.
 */
const guard = EditorState.transactionFilter.of(tr => {
    if (!tr.docChanged || !["input", "delete", "move"].some(e => tr.isUserEvent(e))) return tr;
    const list = tr.startState.field(cards).list;
    if (!list.length) return tr;
    const changes = [];
    tr.changes.iterChanges((fromA, toA, fromB, toB, text) => changes.push({fromA, toA, text: text.toString()}));
    const userEvent = tr.annotation(Transaction.userEvent), start = tr.startState.selection.main;
    const insert = (at, text, cursor) => ({changes: {from: at, insert: text}, selection: {anchor: cursor}, scrollIntoView: true, userEvent});
    if (changes.length === 1 && changes[0].fromA === changes[0].toA) {   // typed or pasted at one place
        const {fromA: at, text} = changes[0];
        for (const c of list) {
            if (at === c.open.from) return insert(at, text.endsWith("\n") ? text : text + "\n", at + text.length);
            if (at === c.close.to) return insert(at, text.startsWith("\n") ? text : "\n" + text, at + (text.startsWith("\n") ? 0 : 1) + text.length);
            if (at > c.open.from && at <= c.open.to) return insert(c.open.to + 1, text, c.open.to + 1 + text.length);
            if (at >= c.close.from && at < c.close.to) return insert(c.close.from - 1, text, c.close.from - 1 + text.length);
        }
        return tr;
    }
    if (tr.isUserEvent("input.api") || tr.isUserEvent("input.replace")) return tr;   // Java's text, and Reemplazar: on purpose
    const broken = list.find(c => breaks(tr, c));
    if (!broken) return tr;
    if (changes.length === 1 && tr.isUserEvent("delete") && start.empty) {   // Backspace or Delete beside a fence
        if (!codeOf(tr.startState, broken).trim()) return removal(tr.startState, broken);
        if (start.head <= broken.open.from) return {selection: {anchor: broken.open.to + 1}, scrollIntoView: true};
        if (start.head >= broken.close.to) return {selection: {anchor: broken.close.from - 1}, scrollIntoView: true};
    }
    return [];
});

/** Where a block of lines goes: in place of the empty line at pos, else after its line (before a card it starts at). */
function ownLines(state, pos, block) {
    const line = state.doc.lineAt(pos), last = line.to === state.doc.length ? "\n" : "";
    const [from, to, insert] = state.field(cards).list.some(c => c.open.from === pos) ? [pos, pos, block + "\n"]
        : !line.text.trim() ? [line.from, line.to, block + last]
        : [line.to, line.to, "\n" + block + last];
    return {from, to, insert, inside: from + insert.indexOf("\n", insert.indexOf(block)) + 1};
}

/** A new code block at pos, in the notebook's language, with the cursor in it; with no language yet, its menu opens. */
function newBlock(view, pos, code = "", lang = codeLanguage) {
    const at = ownLines(view.state, pos, "```" + lang + "\n" + code + "\n```");
    view.dispatch({changes: at, selection: {anchor: at.inside + code.length}, scrollIntoView: true, userEvent: "card.add"});
    view.focus();
    if (lang) return;
    setTimeout(() => {   // once scrolled to it
        const card = view.state.field(cards).list.find(c => c.open.to < at.inside && at.inside < c.close.from);
        const head = card && [...view.contentDOM.querySelectorAll(".cm-card-head")].find(h => cardOf(view, h) === card);
        if (head) openMenu(view, head.querySelector(".chip"), card);
    }, 80);
}

// ---------- The menu of a block's language ----------

let menu = null;

function closeMenu() {
    menu?.remove();
    menu = null;
}

/** Under a card's language: the first languages, Salida, and every other on request. */
function openMenu(view, chip, card) {
    closeMenu();
    if (!card) return;
    const from = card.open.from, now = describe(card.info);
    const pick = id => {
        closeMenu();
        setLanguage(view, from, id);
    };
    menu = element("div", "lang-menu");
    menu.anchor = chip;
    for (const l of FIRST) menu.append(option(dot(l.color), l.name, l === now ? words.current : l.hint, () => pick(l.id), l === now));
    menu.append(element("hr", ""), option(icon("terminal"), words.outputLong, now.output ? words.current : "", () => pick(OUTPUT), now.output),
        option(null, words.more, "", () => more(view, chip, pick)));
    document.body.append(menu);
    place(chip);
}

function option(mark, name, hint, act, current = false) {
    const o = element("div", "option" + (current ? " current" : ""));
    if (mark) o.append(mark);
    o.append(element("span", "name", name));
    if (hint) o.append(element("span", "hint", hint));
    o.addEventListener("mousedown", e => e.preventDefault());
    o.addEventListener("click", act);
    return o;
}

/** Every language CodeMirror knows, found by typing, and any other word typed in. */
function more(view, chip, pick) {
    const find = element("input", "find"), list = element("div", "list");
    find.placeholder = words.find;
    const all = languages.filter(l => !FIRST.some(f => f.data === l)).map(l => ({id: idOf(l), name: l.name}))
        .filter(l => l.id).sort((a, b) => a.name.localeCompare(b.name, "es"));
    const show = () => {
        const typed = find.value.trim(), wanted = fold(typed);
        const found = all.filter(l => fold(l.name).includes(wanted) || l.id.includes(wanted));
        list.replaceChildren(...found.map(l => option(null, NAMES[l.name] ?? l.name, l.id, () => pick(l.id))));
        if (typed && !found.some(l => l.id === wanted)) list.append(option(null, `${words.use} «${idIn(typed)}»`, "", () => pick(idIn(typed))));
    };
    find.addEventListener("input", show);
    find.addEventListener("keydown", e => {
        if (e.key === "Enter") list.firstChild?.click();
        if (e.key === "Escape") view.focus();
    });
    menu.replaceChildren(find, list);
    show();
    place(chip);
    find.focus();
}

/** The menu under its chip, or above it when there is more room there; scrolling when it fits neither way. */
function place(chip) {
    const r = chip.getBoundingClientRect(), below = window.innerHeight - r.bottom - 12, above = r.top - 12;
    menu.style.maxHeight = "";
    const down = menu.offsetHeight <= below || below >= above, room = down ? below : above;
    if (menu.offsetHeight > room) menu.style.maxHeight = room + "px";
    menu.style.left = Math.max(8, Math.min(r.left, window.innerWidth - menu.offsetWidth - 8)) + "px";
    menu.style.top = (down ? r.bottom + 4 : r.top - 4 - menu.offsetHeight) + "px";
}

/** A card's language, written after its ```; the notebook may keep it for its next blocks. */
function setLanguage(view, from, id) {
    const card = view.state.field(cards).list.find(c => c.open.from === from);
    if (!card) return;
    const [, marks, rest] = /^(\s*(?:`{3,}|~{3,}))\s*\S*(.*)$/.exec(card.open.text);
    view.dispatch({changes: {from: card.open.from, to: card.open.to, insert: marks + id + rest}, userEvent: "card.language"});
    if (id !== OUTPUT) tell("language:" + id);
    view.focus();
}

document.addEventListener("mousedown", e => {
    if (menu && !menu.contains(e.target) && !menu.anchor.contains(e.target)) closeMenu();
}, true);
document.addEventListener("keydown", e => {
    if (menu && e.key === "Escape") {
        closeMenu();
        e.preventDefault();
    }
}, true);

// ---------- Writing: Enter after ```, and the formatting bar ----------

/** Enter after ```java on a line of its own closes the block, with an empty line for the code. */
function closeFence(view) {
    const {state} = view, {head, empty} = state.selection.main, line = state.doc.lineAt(head);
    const marks = /^(`{3,}|~{3,})[^`]*$/.exec(line.text);
    if (!empty || head !== line.to || !marks) return false;
    let block = syntaxTree(state).resolveInner(line.from, 1);
    while (block && block.name !== "FencedCode") block = block.parent;
    if (!block || block.from !== line.from || block.parent?.name !== "Document" || block.getChildren("CodeMark").length > 1) return false;
    view.dispatch({changes: {from: line.to, insert: "\n\n" + marks[1]}, selection: {anchor: line.to + 1}, scrollIntoView: true, userEvent: "input"});
    return true;
}

/** **bold**, *italic* or $math$ around each selection, or off when it already is. */
function wrap(view, mark) {
    const {state} = view, n = mark.length;
    view.dispatch(state.changeByRange(range => {
        const on = state.sliceDoc(range.from - n, range.from) === mark && state.sliceDoc(range.to, range.to + n) === mark
            && (mark !== "*" || state.sliceDoc(range.from - 2, range.from) !== "**");
        return on
            ? {changes: [{from: range.from - n, to: range.from}, {from: range.to, to: range.to + n}], range: EditorSelection.range(range.from - n, range.to - n)}
            : {changes: [{from: range.from, insert: mark}, {from: range.to, insert: mark}], range: EditorSelection.range(range.from + n, range.to + n)};
    }), {scrollIntoView: true, userEvent: "input.format"});
}

/** A mark at the start of every selected line ("## ", "- ", "- [ ] "), or off when they all have it. */
function prefix(view, has, mark) {
    const {state} = view, numbers = new Set();
    for (const r of state.selection.ranges) {
        for (let n = state.doc.lineAt(r.from).number; n <= state.doc.lineAt(r.to).number; n++) numbers.add(n);
    }
    const lines = [...numbers].map(n => state.doc.line(n)), on = lines.every(l => has.test(l.text));
    view.dispatch({
        changes: lines.map(l => {
            const old = (on ? has : /^(#{1,6} |- \[[ xX]\] |[-*+] |\d+[.)] )?/).exec(l.text)[0];
            return {from: l.from, to: l.from + old.length, insert: on ? "" : mark};
        }), scrollIntoView: true, userEvent: "input.format"
    });
}

/** The buttons of the formatting bar, and Ctrl+B / Ctrl+I; inside code they leave it as it is. */
function format(view, kind) {
    const {state} = view, {from, to, head} = state.selection.main;
    if (inCode(state, head)) return true;
    if (kind === "bold") wrap(view, "**");
    else if (kind === "italic") wrap(view, "*");
    else if (kind === "heading") prefix(view, /^#{1,6} /, "## ");
    else if (kind === "list") prefix(view, /^[-*+] (?!\[[ xX]\] )/, "- ");
    else if (kind === "check") prefix(view, /^- \[[ xX]\] /, "- [ ] ");
    else if (kind === "link") {   // [[ ]] with the titles of the notes to pick from
        const text = state.sliceDoc(from, to);
        view.dispatch({changes: {from, to, insert: "[[" + text + "]]"}, selection: {anchor: from + 2 + text.length}, userEvent: "input.format"});
        if (!text) startCompletion(view);
    } else if (kind === "math") {   // inline around what is selected, else a formula on its own lines
        if (from < to) wrap(view, "$");
        else {
            const at = ownLines(state, head, "$$\n\n$$");
            view.dispatch({changes: at, selection: {anchor: at.inside}, scrollIntoView: true, userEvent: "input.format"});
        }
    }
    view.focus();
    return true;
}

// ---------- [[links]], #tags and the clipboard ----------

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

// ---------- The editor ----------

const extensions = () => [
    highlightSpecialChars(), history(), drawSelection(), dropCursor(), indentOnInput(), bracketMatching(),
    closeBrackets(), highlightSelectionMatches(), placeholder(words.placeholder), EditorState.phrases.of(phrases),
    markdown({base: markdownLanguage, codeLanguages: language}),
    highlighters.map(h => syntaxHighlighting(h)),
    cards, guard, lineUp,
    Prec.high(keymap.of([
        {key: "Enter", run: closeFence},
        {key: "Mod-b", run: view => format(view, "bold")},
        {key: "Mod-i", run: view => format(view, "italic")},
    ])),
    marking(LINK, "cm-wikilink"), marking(TAG, "cm-tag", text => /\p{L}/u.test(text)), followLinks, clipboard,
    autocompletion({override: [linkCompletions], icons: false}),
    EditorView.lineWrapping,
    keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, indentWithTab]),
    EditorView.updateListener.of(u => {
        if (u.docChanged) {
            tell("changed");
            closeMenu();
        } else if (menu && u.geometryChanged) {
            place(menu.anchor);
        }
    }),
];

const editor = document.getElementById("editor"), reading = document.getElementById("reading");
const view = new EditorView({parent: editor, state: EditorState.create({extensions: extensions()})});
view.scrollDOM.addEventListener("scroll", closeMenu);
window.editorView = view;   // for the tests: where CodeMirror puts each line, and where a click lands

/** Accents and case do not matter when searching, and a folded text keeps the positions of the original. */
function fold(text) {
    let out = "";
    for (let i = 0; i < text.length; i++) out += text[i].normalize("NFD").replace(/\p{M}/gu, "").toLowerCase()[0] ?? text[i];
    return out;
}

// Called by Java. What Java types goes in as typing does ("input.api"), beside cards too.
window.setText = text => {
    closeMenu();
    view.setState(EditorState.create({doc: text, extensions: extensions()}));
};
window.getText = () => view.state.doc.toString();
window.setTitles = lines => titles = lines ? lines.split("\n") : [];
window.setTheme = theme => document.documentElement.dataset.theme = theme;
window.setCodeLanguage = id => codeLanguage = id ?? "";
window.insertText = text => view.dispatch(view.state.replaceSelection(text), {scrollIntoView: true, userEvent: "input.api"});
window.insertCode = (code, lang) => {   // as it is inside a code block; in a new block outside one
    const {state} = view, head = state.selection.main.head, list = state.field(cards).list;
    const inCard = list.some(c => c.open.to < head && head < c.close.from);
    const beside = list.some(c => c.open.from === head || c.close.to === head);
    if (inCard || (!beside && inCode(state, head))) view.dispatch(state.replaceSelection(code), {scrollIntoView: true, userEvent: "input.api"});
    else newBlock(view, head, code, lang);
    view.focus();
};
window.newBlock = () => {   // after the card with the cursor, or where the cursor is
    const head = view.state.selection.main.head, card = view.state.field(cards).list.find(c => c.open.from <= head && head <= c.close.to);
    newBlock(view, card ? card.close.to : head);
};
window.format = kind => format(view, kind);
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
    closeMenu();
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

// A code block of the reading view becomes a card as while writing: its language and a Copy button
// above numbered lines with the same colors. A block of output is plain text.
function card(code) {
    const info = code.className.startsWith("language-") ? code.className.slice("language-".length) : "";
    const kind = describe(info), text = code.textContent.replace(/\n$/, "");
    const copy = element("button", "copy", words.copy);
    copy.addEventListener("click", () => {
        tell("copy:" + text);
        say(copy, words.copied, words.copy);
    });
    const label = kind.unset ? element("span", "lang", words.code)
        : element("span", "lang", kind.output ? icon("terminal") : dot(kind.color), kind.name);
    const box = element("div", "code-card" + (kind.output ? " output" : "")), pre = code.parentElement;
    pre.replaceWith(box);
    box.append(element("div", "code-head", label, copy), pre);
    if (kind.output) return;
    lines(code, text, null);
    language(info)?.load().then(support => lines(code, text, support.language.parser.parse(text)));
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
