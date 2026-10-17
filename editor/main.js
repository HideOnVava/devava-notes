// The page of Devava Notes' editor: CodeMirror 6 to write a note, and the reading view that Java
// renders with commonmark-java. `npm run build` bundles it into
// src/main/resources/com/devavaxp/notes/editor.js, which is committed.
import "./keys.js";
import {ChangeSet, EditorSelection, EditorState, Prec, RangeSetBuilder, StateEffect, StateField, Transaction} from "@codemirror/state";
import {
    Decoration, EditorView, MatchDecorator, ViewPlugin, WidgetType, drawSelection, dropCursor, highlightSpecialChars, keymap, placeholder,
    showTooltip
} from "@codemirror/view";
import {defaultKeymap, history, historyKeymap, indentWithTab, isolateHistory, redo, undo} from "@codemirror/commands";
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
    block: "Bloque de código", drawEdit: "Editar dibujo", drawHint: "Clic para dibujar en él",
    diagram: "Diagrama", diagramLong: "Diagrama (Mermaid)", diagramEmpty: "Escribe arriba el diagrama: su primera línea dice cuál es (flowchart, classDiagram, erDiagram…)",
    diagramType: "No se reconoce el diagrama: su primera línea dice cuál es (flowchart, classDiagram, erDiagram, sequenceDiagram, stateDiagram-v2…)",
    diagramLine: "Error en la línea", diagramMissing: "No se pudo cargar Mermaid para dibujar el diagrama",
    backlinks: "Notas que enlazan aquí",
    small: "Chica", medium: "Mediana", large: "Grande", original: "Original", resize: "Arrastra para cambiar su tamaño",
    mathBrace: "Falta cerrar una llave }", mathUnknown: "No existe el comando", mathPower: "Falta lo que va de exponente",
    mathIndex: "Falta lo que va de subíndice", mathTwoPowers: "Dos exponentes seguidos: júntalos entre { }",
    mathTwoIndexes: "Dos subíndices seguidos: júntalos entre { }", mathWrong: "Así no se puede dibujar todavía",
    table: "Tabla", row: "Fila", column: "Columna", tableMore: "Más: filas, columnas y alineación",
    rowAbove: "Insertar fila arriba", columnLeft: "Insertar columna a la izquierda", removeHeader: "Quitar el encabezado",
    left: "Alinear a la izquierda", center: "Centrar", right: "Alinear a la derecha",
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
// A block of `mermaid` is a diagram written as text (flowchart, classDiagram, erDiagram…), which Mermaid draws.
const DIAGRAM = "mermaid";
const ALSO = {tsql: "MS SQL", mssql: "MS SQL", mariadb: "MariaDB SQL", asm: "Gas", masm: "Gas", nasm: "Gas"};
// language-data's names the app shows otherwise, as Java does (Editor.languageName).
const NAMES = {"Gas": "Ensamblador", "Shell": "Bash", "MS SQL": "T-SQL"};

/** The language word of a block's info: "java" in ```java. */
const idIn = info => info.trim().split(/\s/)[0];

/** What CodeMirror colors a block with: its language ("java", "c++", "tsql"…), if it knows it. */
function language(info) {
    const id = idIn(info).toLowerCase();
    return !id || id === OUTPUT || id === DIAGRAM ? null : LanguageDescription.matchLanguageName(languages, ALSO[id] ?? id, true);
}

/** How a block shows: its language's name and color, Salida, Diagrama, or an invitation to choose a language. */
function describe(info) {
    const id = idIn(info);
    if (!id) return {id, name: words.choose, unset: true};
    if (id.toLowerCase() === OUTPUT) return {id: OUTPUT, name: words.output, output: true};
    if (id.toLowerCase() === DIAGRAM) return {id: DIAGRAM, name: words.diagram, diagram: true};
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
    pen: "M4 20h4L19 9l-4-4L4 16v4z",
    table: "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M3 10h18 M9 10v10",
    dots: "M5 12h.01 M12 12h.01 M19 12h.01",
    diagram: "M4 4h7v5H4z M13 15h7v5h-7z M7.5 9v3h9v3",
    back: "M9 14l-5-5 5-5 M4 9h10a6 6 0 0 1 0 12h-3",
    // a callout's kinds
    book: "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5z M4 19a2 2 0 0 1 2-2h13",
    list: "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01",
    alert: "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z M12 7v6 M12 16.5h.01",
    warning: "M12 3l9.5 17h-19z M12 10v4 M12 17h.01",
    info: "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z M12 11v6 M12 7.5h.01",
    bulb: "M9 18h6 M10 21h4 M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.3 1 2.1h5c0-.8.4-1.6 1-2.1A6 6 0 0 0 12 3z",
    check: "M5 12.5l4.5 4.5L19 7",
    help: "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .9-1 1.7 M12 17h.01",
    x: "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z M9 9l6 6 M15 9l-6 6",
    quote: "M5 7h5v5H7v1a3 3 0 0 0 3 3 M14 7h5v5h-3v1a3 3 0 0 0 3 3",
    summary: "M6 3h9l4 4v14H6z M14 3v5h5 M9 13h7 M9 17h5",
    todo: "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z M8.5 12.5l2.5 2.5 4.5-5",
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

/** What goes before a block's name: a console for Salida, a diagram for Diagrama, else its language's dot. */
function mark(kind) {
    return kind.output ? icon("terminal") : kind.diagram ? icon("diagram") : dot(kind.color);
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
        const chip = button("chip" + (kind.unset ? " unset" : ""), mark(kind), kind.name, icon("down"));
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

/**
 * The bottom of a diagram's card: the diagram as Mermaid draws its code, below it, drawn again
 * once typing pauses; a line below it when the cursor is right after it.
 */
class DiagramFoot extends WidgetType {
    constructor(code, here) {
        super();
        this.code = code;
        this.here = here;
    }

    eq(other) {
        return other.code === this.code && other.here === this.here;
    }

    toDOM(view) {
        const box = element("div", "diagram");
        const dom = element("div", "cm-card-bottom", element("div", "cm-card-foot cm-diagram", box));
        this.updateDOM(dom, view, null);
        return dom;
    }

    // The same box takes the new code: its drawing stays until the new one is ready, so typing does not make it jump.
    updateDOM(dom, view, old) {
        dom.className = "cm-card-bottom" + (this.here ? " here" : "");
        const box = dom.querySelector(".diagram");
        if (old && old.code === this.code) return true;
        box.dataset.code = this.code;
        clearTimeout(box.timer);
        box.timer = setTimeout(() => drawDiagram(box, dark()).then(() => view.requestMeasure()), box.firstChild ? 400 : 0);
        return true;
    }

    get estimatedHeight() {
        return 240;
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
            const card = {open, close, info: info ? doc.sliceString(info.from, info.to) : ""}, kind = describe(card.info);
            list.push(card);
            decorations.add(open.from, open.to, Decoration.replace({widget: new Head(card.info, cursor === open.from), block: true}));
            for (let n = open.number + 1; n < close.number; n++) {
                decorations.add(doc.line(n).from, doc.line(n).from, kind.output ? outputLine
                    : Decoration.line({class: "cm-code cm-card-line", attributes: {"data-n": String(n - open.number)}}));
            }
            const foot = kind.diagram ? new DiagramFoot(codeOf(state, card), cursor === close.to) : new Foot(cursor === close.to);
            decorations.add(close.from, close.to, Decoration.replace({widget: foot, block: true}));
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

// ---------- Diagrams (```mermaid) ----------

const dark = () => document.documentElement.dataset.theme === "dark";

// Mermaid is big: its bundle (diagrams.js, next to this page's) loads the first time a note shows a diagram.
let mermaidLoad = null;

function mermaidReady() {
    return mermaidLoad ??= new Promise((resolve, reject) => {
        const script = element("script", "");
        script.src = "diagrams.js";
        script.onload = () => resolve(window.mermaid);
        script.onerror = () => {
            mermaidLoad = null;
            reject(new Error("diagrams.js did not load"));
        };
        document.head.append(script);
    });
}

// The app's colors (editor.css) for Mermaid's base theme, and its font. strict: no HTML or links that run anything.
const font = '"Segoe UI", system-ui, -apple-system, "Helvetica Neue", Arial, sans-serif';
const DIAGRAM_THEMES = {
    light: {
        background: "#FFFFFF", primaryColor: "#E7ECFF", primaryBorderColor: "#2B4FD8", primaryTextColor: "#161A27",
        secondaryColor: "#E3F4EF", tertiaryColor: "#F6F7FB", lineColor: "#596074", edgeLabelBackground: "#FFFFFF",
        noteBkgColor: "#FFF6D8", noteBorderColor: "#D9B44A", noteTextColor: "#161A27", fontFamily: font, fontSize: "14px",
        pie1: "#9DB0F5", pie2: "#8FD3C1", pie3: "#F5C38E", pie4: "#C9A6F0", pie5: "#F2A3A3", pie6: "#A8D98F",
        pieStrokeColor: "#FFFFFF", pieOuterStrokeColor: "#596074",
    },
    dark: {
        darkMode: true, background: "#171A23", primaryColor: "#232B52", primaryBorderColor: "#7F95FF", primaryTextColor: "#E8EAF2",
        secondaryColor: "#173A33", tertiaryColor: "#1C2030", lineColor: "#A1A8BB", edgeLabelBackground: "#171A23",
        noteBkgColor: "#3A3320", noteBorderColor: "#8A7A3A", noteTextColor: "#E8EAF2", fontFamily: font, fontSize: "14px",
        pie1: "#4A5BC4", pie2: "#2E8C76", pie3: "#B57A2E", pie4: "#7B52B8", pie5: "#B54848", pie6: "#5E9A3E",
        pieStrokeColor: "#171A23", pieOuterStrokeColor: "#A1A8BB",
    },
};

// One diagram at a time: Mermaid's settings (the theme) are its own, for whatever it draws next.
let diagramQueue = Promise.resolve(), diagramIds = 0;

/** A diagram's SVG ({svg}), or what is wrong with its code ({error}), in the dark theme or the light one. */
function mermaidDraw(code, inDark) {
    const job = diagramQueue.then(async () => {
        const mermaid = await mermaidReady();
        mermaid.initialize({startOnLoad: false, securityLevel: "strict", suppressErrorRendering: true, fontFamily: font,
            theme: "base", themeVariables: inDark ? DIAGRAM_THEMES.dark : DIAGRAM_THEMES.light});
        try {
            return {svg: (await mermaid.render("diagram-" + ++diagramIds, code)).svg};
        } catch (e) {
            return {error: explain(e)};
        }
    });
    diagramQueue = job.catch(() => {});
    return job;
}

/**
 * Mermaid's complaint, in the app's words where it can: "Parse error on line 3:" is "Error en la línea 3:",
 * followed by where in that line (a piece of it and a ^), without the list of what Mermaid expected there.
 */
function explain(e) {
    const text = String(e?.message ?? e ?? "").trim();
    if (/No diagram type detected/i.test(text)) return words.diagramType;
    const lines = text.split("\n"), where = /^(?:Parse|Lexical) error on line (\d+)/i.exec(lines[0]);
    return (where ? [`${words.diagramLine} ${where[1]}:`, ...lines.slice(1, 3)] : lines.slice(0, 3)).join("\n");
}

/**
 * A diagram's box drawn from its code (its data-code): its SVG, or what is wrong with its code below
 * its last drawing, faded. Settles once drawn, whatever the code.
 */
function drawDiagram(box, inDark) {
    const code = box.dataset.code;
    if (!code.trim()) {
        box.replaceChildren(element("p", "diagram-hint", words.diagramEmpty));
        return Promise.resolve();
    }
    return mermaidDraw(code, inDark).then(({svg, error}) => {
        if (box.dataset.code !== code) return;   // newer code is on its way
        const drawn = box.querySelector(".diagram-drawing") ?? element("div", "diagram-drawing");
        if (svg) {
            drawn.innerHTML = svg;
            box.replaceChildren(drawn);
        } else {
            box.replaceChildren(...(drawn.firstChild ? [drawn] : []), element("p", "diagram-error", error));
        }
        box.classList.toggle("broken", !svg);
    }, () => box.replaceChildren(element("p", "diagram-error", words.diagramMissing)));
}

/** The diagrams on the page drawn again, in the theme it has now. */
function redrawDiagrams() {
    document.querySelectorAll(".diagram[data-code]").forEach(box => drawDiagram(box, dark()).then(() => view.requestMeasure()));
}

// ---------- Callouts (Obsidian's) ----------

// A quote whose first line starts with [!type] is a callout: a box of its kind's color, with its icon and its name,
// or the title written after [!type]. The app writes Obsidian's words (definition, example, important, warning…),
// which Obsidian reads too, and reads Spanish ones as well: [!advertencia] is a warning.
const CALLOUT_WORDS = {
    note: ["note", "Nota"], info: ["info", "Información"], definition: ["definition", "Definición"], todo: ["todo", "Pendiente"],
    abstract: ["abstract", "Resumen"], summary: ["abstract", "Resumen"], tldr: ["abstract", "Resumen"],
    tip: ["tip", "Consejo"], hint: ["tip", "Pista"], important: ["important", "Importante"],
    success: ["success", "Hecho"], check: ["success", "Hecho"], done: ["success", "Hecho"],
    question: ["question", "Pregunta"], help: ["question", "Ayuda"], faq: ["question", "Preguntas frecuentes"],
    warning: ["warning", "Advertencia"], caution: ["warning", "Precaución"], attention: ["warning", "Atención"],
    failure: ["failure", "Fallo"], fail: ["failure", "Fallo"], missing: ["failure", "Falta"],
    danger: ["danger", "Peligro"], error: ["danger", "Error"], bug: ["bug", "Error de código"],
    example: ["example", "Ejemplo"], quote: ["quote", "Cita"], cite: ["quote", "Cita"],
    nota: ["note", "Nota"], informacion: ["info", "Información"], definicion: ["definition", "Definición"],
    pendiente: ["todo", "Pendiente"], resumen: ["abstract", "Resumen"], consejo: ["tip", "Consejo"], pista: ["tip", "Pista"],
    importante: ["important", "Importante"], hecho: ["success", "Hecho"], pregunta: ["question", "Pregunta"],
    ayuda: ["question", "Ayuda"], advertencia: ["warning", "Advertencia"], precaucion: ["warning", "Precaución"],
    cuidado: ["warning", "Cuidado"], atencion: ["warning", "Atención"], aviso: ["warning", "Aviso"],
    fallo: ["failure", "Fallo"], peligro: ["danger", "Peligro"], ejemplo: ["example", "Ejemplo"], cita: ["quote", "Cita"],
};
const CALLOUT_ICONS = {
    note: "pen", info: "info", definition: "book", todo: "todo", abstract: "summary", tip: "bulb", important: "alert",
    success: "check", question: "help", warning: "warning", failure: "x", danger: "x", bug: "x", example: "list", quote: "quote",
};

/** A callout's kind by the word in its [!…], in any case and with or without accents: an unknown word is a note named as written. */
function calloutKind(word) {
    const [family, name] = CALLOUT_WORDS[fold(word)] ?? ["note", word.charAt(0).toUpperCase() + word.slice(1)];
    return {family, name, icon: CALLOUT_ICONS[family]};
}

// The first line of a callout: its > and its [!type], folded (-) or not (+) in Obsidian.
const CALLOUT_LINE = /^(\s*>[ \t]?)\[!([^\]\s]+)\]([+-]?)/;

/** While writing, a callout's [!type] shows as its icon and name; a Backspace after it shows the word again. */
class CalloutMark extends WidgetType {
    constructor(kind) {
        super();
        this.kind = kind;
    }

    eq(other) {
        return other.kind.family === this.kind.family && other.kind.name === this.kind.name;
    }

    toDOM() {
        const mark = element("span", "cm-callout-mark", icon(this.kind.icon), this.kind.name);
        mark.dataset.callout = this.kind.family;
        return mark;
    }

    ignoreEvent() {
        return false;
    }
}

/** The callouts while writing: their lines in their kind's colors, as a box, and their [!type] as its icon and name. */
function calloutDecorations(state) {
    const builder = new RangeSetBuilder();
    syntaxTree(state).iterate({
        enter: node => {
            if (node.name !== "Blockquote") return;   // a quote inside a list item is found further in
            const first = state.doc.lineAt(node.from), m = CALLOUT_LINE.exec(first.text);
            if (!m) return false;
            const kind = calloutKind(m[2]), last = state.doc.lineAt(node.to);
            for (let n = first.number; n <= last.number; n++) {
                const line = state.doc.line(n), edges = (n === first.number ? " cm-callout-first" : "") + (n === last.number ? " cm-callout-last" : "");
                builder.add(line.from, line.from, Decoration.line({class: "cm-callout" + edges, attributes: {"data-callout": kind.family}}));
                if (n === first.number) builder.add(line.from + m[1].length, line.from + m[0].length, Decoration.replace({widget: new CalloutMark(kind)}));
            }
            return false;
        }
    });
    return builder.finish();
}

const callouts = StateField.define({
    create: calloutDecorations,
    update: (value, tr) => tr.docChanged || syntaxTree(tr.state) !== syntaxTree(tr.startState) ? calloutDecorations(tr.state) : value,
    provide: field => EditorView.decorations.from(field),
});

/**
 * A callout of the reading view: its quote as a box of its kind's color, its first line (what follows
 * its [!type], else its kind's name) as its title, with its icon.
 */
function callout(quote) {
    const first = quote.firstElementChild, text = first?.tagName === "P" ? first.firstChild : null;
    const m = text?.nodeType === Node.TEXT_NODE ? /^\[!([^\]\s]+)\][+-]?[ \t]*/.exec(text.data) : null;
    if (!m) return;
    const kind = calloutKind(m[1]), title = element("span", "");
    text.data = text.data.slice(m[0].length);
    for (let node = first.firstChild; node;) {   // the title: up to the end of the first line; the rest of it is text
        const next = node.nextSibling;
        if (node.nodeName === "BR") {
            node.remove();
            break;
        }
        if (node.nodeType === Node.TEXT_NODE && node.data.includes("\n")) {
            title.append(node.data.slice(0, node.data.indexOf("\n")));
            node.data = node.data.slice(node.data.indexOf("\n") + 1);
            break;
        }
        title.append(node);
        node = next;
    }
    if (!title.textContent.trim()) title.replaceChildren(kind.name);
    if (!first.textContent.trim() && !first.querySelector("img")) first.remove();   // the first line was all there was
    quote.classList.add("callout");
    quote.dataset.callout = kind.family;
    quote.prepend(element("div", "callout-title", icon(kind.icon), title));
}

// ---------- Formulas ----------

/** The formula ($…$, or $$…$$ on lines of its own, as Java reads them) the position is in: where its text starts and ends. */
function formulaAt(state, pos) {
    const text = state.doc.toString();
    for (let i = 0; i < text.length; i++) {
        if (text[i] === "\\") {
            i++;
        } else if (text[i] === "$" && !inCode(state, i)) {
            const end = mathEnd(text, i), marks = text[i + 1] === "$" ? 2 : 1;
            if (end < 0) continue;
            if (i + marks <= pos && pos <= end - marks) return {from: i + marks, to: end - marks, display: marks === 2};
            i = end - 1;
        }
    }
    return null;
}

/**
 * A command (\alpha) ends before what comes next: before a letter, which would join its name, a space; at the end
 * of a $formula$, where a space before the $ would undo it, {}; on lines of its own, a space.
 */
function ended(piece, next, display) {
    if (!/\\[a-zA-Z]+$/.test(piece)) return piece;
    return piece + (/[a-zA-Z]/.test(next) || display ? " " : next === "$" ? "{}" : "");
}

// A piece of a formula from the palette (\frac{‸}{}, with ‸ where the cursor goes): in the formula with the cursor,
// else in a new one; what is selected goes where the cursor would.
window.insertMath = latex => {
    const at = latex.indexOf("‸"), cell = cellNow();
    if (cell) {   // $…$ in a table's cell: in the one with the caret, else a new one
        const {start, end} = caret(cell), text = cell.textContent, picked = text.slice(start, end);
        const wrap = (text.slice(0, start).match(/(?<!\\)\$/g) ?? []).length % 2 ? "" : "$";
        const piece = ended(latex.replace("‸", picked), wrap || text.charAt(end), false);
        writeCell(view, cell, text.slice(0, start) + wrap + piece + wrap + text.slice(end), start + wrap.length + (at < 0 ? piece.length : at + picked.length));
        return;
    }
    const {state} = view, {from, to} = state.selection.main;
    if (inCode(state, from)) return;
    const inside = formulaAt(state, from), picked = state.sliceDoc(from, to), wrap = inside && to <= inside.to ? "" : "$";
    const piece = ended(latex.replace("‸", picked), wrap || state.sliceDoc(to, to + 1), !wrap && inside.display);
    view.dispatch({changes: {from, to, insert: wrap + piece + wrap}, selection: {anchor: from + wrap.length + (at < 0 ? piece.length : at + picked.length)},
        scrollIntoView: true, userEvent: "input.math"});
    view.focus();
};

// While writing a formula, it shows drawn below it, as Vista previa draws it, or what is wrong with it.
function formulaTooltip(state) {
    const {main} = state.selection, formula = main.empty ? formulaAt(state, main.head) : null, tex = formula && state.sliceDoc(formula.from, formula.to);
    if (!tex?.trim()) return null;
    return {
        pos: formula.to, above: false, arrow: false,
        create: () => {
            const dom = element("div", "cm-formula");
            try {
                katex.render(tex, dom, {displayMode: formula.display, throwOnError: true});
            } catch (e) {
                const detail = String(e?.message ?? e).replace(/^KaTeX parse error: /, "");
                dom.classList.add("wrong");
                dom.replaceChildren(element("div", "said", formulaError(detail)), element("div", "detail", detail));
            }
            return {dom};
        },
    };
}

/** What KaTeX found wrong with a formula, said for someone who does not know LaTeX. */
function formulaError(detail) {
    const unknown = /Undefined control sequence: (\\\S+)/.exec(detail);
    if (/expected '}'/i.test(detail)) return words.mathBrace;
    if (unknown) return `${words.mathUnknown} ${unknown[1]}`;
    if (/Expected group after '\^'/.test(detail)) return words.mathPower;
    if (/Expected group after '_'/.test(detail)) return words.mathIndex;
    if (/Double superscript/.test(detail)) return words.mathTwoPowers;
    if (/Double subscript/.test(detail)) return words.mathTwoIndexes;
    return words.mathWrong;
}

const formulaPreview = StateField.define({
    create: formulaTooltip,
    update: (tooltip, tr) => tr.docChanged || tr.selection ? formulaTooltip(tr.state) : tooltip,
    provide: field => showTooltip.from(field),
});

// ---------- Pictures while writing ----------

// A picture of the note (![…](…)), while writing: below its line, at its size, which is Obsidian's (![|400](…), in
// pixels after its words). Its corner drags it bigger or smaller, and over it three sizes and the original's.
const IMAGE = /!\[([^\]\n]*)\]\((<[^>\n]+>|[^)\s]+)(?:\s+"[^"\n]*")?\)/g;
const SIZED = /\|(\d+)(?:x(\d+))?\s*$/;
const SIZES = [[words.small, 200], [words.medium, 400], [words.large, 600], [words.original, 0]];

/** The open note's folder, where its pictures are (Java says, with the note); a stamp that makes them load again once one changes. */
let noteBase = "", pictureStamp = Date.now();
const refreshPictures = StateEffect.define();

/** Where a picture is, as this page loads it: next to the note (attachments/…), or as written when it has a scheme. */
function pictureUrl(target) {
    const src = target.replace(/^<|>$/g, "");
    if (/^[a-z][a-z0-9+.-]*:/i.test(src)) return src;
    try {
        return new URL(src, noteBase).href + "?t=" + pictureStamp;
    } catch {
        return "";
    }
}

/** A picture's link with its size (px; 0, none: the original's), written after its words. */
function resizePicture(view, dom, target, px) {
    const line = view.state.doc.lineAt(view.posAtDOM(dom));
    for (const m of line.text.matchAll(IMAGE)) {
        if (m[2] !== target) continue;
        const from = line.from + m.index + 2, alt = m[1].replace(SIZED, "");
        view.dispatch({changes: {from, to: from + m[1].length, insert: px ? `${alt}|${Math.round(px)}` : alt}, userEvent: "input.picture"});
        return;
    }
}

class Picture extends WidgetType {
    constructor(target, alt) {
        super();
        this.target = target;
        this.alt = alt;
        this.stamp = pictureStamp;
    }

    eq(other) {
        return other.target === this.target && other.alt === this.alt && other.stamp === this.stamp;
    }

    toDOM(view) {
        const img = element("img", ""), sizes = element("div", "cm-picture-sizes"), handle = element("div", "cm-picture-handle");
        const box = element("div", "cm-picture-box", img, sizes, handle), dom = element("div", "cm-picture", box);
        const width = +(SIZED.exec(this.alt)?.[1] ?? 0), drawing = /\.excalidraw\.png>?$/i.test(this.target);
        img.src = pictureUrl(this.target);
        img.alt = this.alt.replace(SIZED, "");
        if (width) box.style.width = width + "px";
        img.addEventListener("load", () => {
            if (!width && drawing) box.style.width = img.naturalWidth / 2 + "px";   // its PNG is twice as big, to look sharp
            view.requestMeasure();
        });
        if (drawing) {
            img.title = words.drawHint;
            img.addEventListener("click", () => tell("draw:" + this.target.replace(/^<|>$/g, "")));
        }
        for (const [name, px] of SIZES) {
            const size = button("act", name);
            size.addEventListener("click", () => resizePicture(view, dom, this.target, px));
            sizes.append(size);
        }
        handle.title = words.resize;
        handle.addEventListener("mousedown", e => {   // as wide as the pointer, written once let go
            e.preventDefault();
            const start = e.clientX, from = box.getBoundingClientRect().width, most = dom.getBoundingClientRect().width;
            const move = m => box.style.width = Math.round(Math.max(40, Math.min(most, from + m.clientX - start))) + "px";
            const up = () => {
                document.removeEventListener("mousemove", move, true);
                document.removeEventListener("mouseup", up, true);
                resizePicture(view, dom, this.target, parseFloat(box.style.width));
            };
            document.addEventListener("mousemove", move, true);
            document.addEventListener("mouseup", up, true);
        });
        return dom;
    }

    get estimatedHeight() {
        return 220;
    }

    ignoreEvent() {
        return true;
    }
}

/** The note's pictures, each below its line; none in code, nor in a table, whose cells show its link. */
function pictureDecorations(state) {
    const builder = new RangeSetBuilder(), text = state.doc.toString();
    for (const m of text.matchAll(IMAGE)) {
        const line = state.doc.lineAt(m.index);
        if (inCode(state, m.index) || line.text.trimStart().startsWith("|")) continue;
        builder.add(line.to, line.to, Decoration.widget({widget: new Picture(m[2], m[1]), block: true, side: 1}));
    }
    return builder.finish();
}

const pictures = StateField.define({
    create: pictureDecorations,
    update: (value, tr) => tr.docChanged || tr.effects.some(e => e.is(refreshPictures)) || syntaxTree(tr.state) !== syntaxTree(tr.startState)
        ? pictureDecorations(tr.state) : value,
    provide: field => EditorView.decorations.from(field),
});

// ---------- The notes that link here ----------

// The notes whose [[links]] lead to this one (Java finds them once the note is open): at its end, while writing
// it and in Vista previa; a click opens one. Each is its title and, when it is in another notebook, that one's name.
let backlinks = [];
const showBacklinks = StateEffect.define();

function backlinkList(list) {
    const box = element("div", "backlinks", element("span", "backlinks-title", icon("back"), words.backlinks));
    list.forEach(({title, where}, i) => {
        const link = button("backlink", title);
        if (where) link.append(element("span", "where", where));
        link.addEventListener("click", () => tell("back:" + i));
        box.append(link);
    });
    return box;
}

class Backlinks extends WidgetType {
    constructor(list) {
        super();
        this.list = list;
    }

    eq(other) {
        return other.list === this.list;
    }

    toDOM() {
        return element("div", "cm-backlinks", backlinkList(this.list));
    }

    get estimatedHeight() {
        return 64;
    }

    ignoreEvent(e) {
        return onButton(e);
    }
}

const backlinkField = StateField.define({
    create: () => backlinks,
    update: (list, tr) => tr.effects.reduce((now, e) => e.is(showBacklinks) ? e.value : now, list),
    provide: field => EditorView.decorations.compute(["doc", field], state => {
        const list = state.field(field);
        return list.length ? Decoration.set(Decoration.widget({widget: new Backlinks(list), block: true, side: 2}).range(state.doc.length))
            : Decoration.none;
    }),
});

/** The notes that link here at the end of the reading view, as they are now. */
function readingBacklinks() {
    reading.querySelector(":scope > .backlinks")?.remove();
    if (backlinks.length) reading.append(backlinkList(backlinks));
}

// ---------- Drawings ----------

// A drawing made in the app (a .excalidraw.png in attachments/), while writing: a button after its
// link opens it in the drawing window, to draw on it again. Java finds the file next to the note.
const DRAWING = /!\[[^\]\n]*\]\(<?(attachments\/[^)>\n]*\.excalidraw\.png)>?\)/gi;

class EditDrawing extends WidgetType {
    constructor(src) {
        super();
        this.src = src;
    }

    eq(other) {
        return other.src === this.src;
    }

    toDOM() {
        const edit = button("cm-draw-edit", icon("pen"), words.drawEdit);
        edit.addEventListener("click", () => tell("draw:" + this.src));
        return edit;
    }
}

const drawingMatcher = new MatchDecorator({
    regexp: DRAWING,
    decorate: (add, from, to, match) => add(to, to, Decoration.widget({widget: new EditDrawing(match[1]), side: 1})),
});

const drawingLinks = ViewPlugin.fromClass(class {
    constructor(view) {
        this.decorations = drawingMatcher.createDeco(view);
    }

    update(u) {
        this.decorations = drawingMatcher.updateDeco(u, this.decorations);
    }
}, {decorations: plugin => plugin.decorations});

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

// ---------- Tables ----------

// A table of the note (Markdown's: | a | b |, then | --- | --- |) shows as a grid whose cells are
// written in, under a bar like a card's. The note keeps the Markdown: a cell that changes rewrites
// that cell, and a change of the grid (a row, a column, an alignment) rewrites the table.

/** Where $math$ that opens at i ends, as Java finds it (Editor.inlineMathEnd: $…$ on one line); -1: it is no math. */
function mathEnd(text, i) {
    if (text[i + 1] === "$") {
        const close = text.indexOf("$$", i + 2);
        return close > i + 2 ? close + 2 : -1;
    }
    if (i + 1 >= text.length || /\s/.test(text[i + 1])) return -1;
    for (let j = i + 1; j < text.length && text[j] !== "\n"; j++) {
        if (text[j] === "\\") j++;
        else if (text[j] === "$") return /\s/.test(text[j - 1]) || /\d/.test(text[j + 1] ?? "") ? -1 : j + 1;
    }
    return -1;
}

/** The $math$ and [[links]] of a row: Java sets them aside before Markdown reads it (Editor.setAside), so their | split no cells. */
function setAside(text) {
    const spans = [];
    for (let i = 0; i < text.length; i++) {
        if (text[i] === "\\") {
            i++;
        } else if (text[i] === "`") {   // what is in code is neither math nor a link (its | still split cells)
            let run = 1;
            while (text[i + run] === "`") run++;
            const close = text.indexOf("`".repeat(run), i + run);
            i = (close < 0 ? i + run : close + run) - 1;
        } else if (text[i] === "$" && mathEnd(text, i) > 0) {
            spans.push([i, mathEnd(text, i)]);
            i = spans.at(-1)[1] - 1;
        } else if (text.startsWith("[[", i)) {
            const close = text.indexOf("]]", i + 2);
            if (close > i + 2 && text.slice(i + 2, close).split("|")[0].trim()) {
                spans.push([i, close + 2]);
                i = close + 1;
            }
        }
    }
    return spans;
}

/** A text with f applied to what is outside its $math$ and [[links]]. */
function outside(text, f) {
    let out = "", at = 0;
    for (const [from, to] of setAside(text)) {
        out += f(text.slice(at, from)) + text.slice(from, to);
        at = to;
    }
    return out + f(text.slice(at));
}

/** A cell as it shows: \| is a |, as Markdown reads it. */
const shown = source => outside(source, piece => piece.replace(/\\\|/g, "|"));
/** A cell as the note keeps it: on one line, and its | escaped, so that they split no cell. */
const kept = text => outside(text.replace(/ /g, " ").replace(/\s*\n\s*/g, " ").trim(), piece => piece.replace(/(?<!\\)\|/g, "\\|"));

/**
 * The cells of a row, split where Markdown splits it: at each | neither escaped nor in math or a
 * link. A cell spans from one | to the next (from, to), its text from start to end, spaces left out.
 */
function rowCells(text) {
    const spans = setAside(text), cuts = [];
    for (let i = 0, s = 0; i < text.length; i++) {
        if (s < spans.length && i >= spans[s][0]) i = spans[s++][1] - 1;
        else if (text[i] === "\\") i++;
        else if (text[i] === "|") cuts.push(i);
    }
    const edges = [-1, ...cuts, text.length], cells = [];
    for (let k = 1; k < edges.length; k++) {
        const from = edges[k - 1] + 1, to = edges[k], piece = text.slice(from, to);
        const start = Math.min(from + piece.length - piece.trimStart().length, to);
        cells.push({from, to, start, end: Math.max(start, to - (piece.length - piece.trimEnd().length))});
    }
    if (cuts.length && !text.slice(0, cuts[0]).trim()) cells.shift();   // the | that opens the row
    if (cuts.length && !text.slice(cuts.at(-1) + 1).trim()) cells.pop();   // and the one that closes it
    return cells;
}

const ALIGNS = {"": "---", left: ":---", center: ":---:", right: "---:"};
const alignOf = mark => mark.startsWith(":") ? (mark.length > 1 && mark.endsWith(":") ? "center" : "left") : mark.endsWith(":") ? "right" : "";

/** A table, from its node: its rows (the header first) with each cell's place and text, and each column's alignment. */
function readTable(state, node, id) {
    const {doc} = state, first = doc.lineAt(node.from), last = doc.lineAt(node.to), rows = [];
    let aligns = [];
    for (let n = first.number; n <= last.number; n++) {
        const line = doc.line(n), cells = rowCells(line.text).map(c => ({from: line.from + c.from, to: line.from + c.to,
            start: line.from + c.start, end: line.from + c.end, source: line.text.slice(c.start, c.end)}));
        if (n === first.number + 1) aligns = cells.map(c => alignOf(c.source));
        else rows.push({line, cells});
    }
    const width = rows[0].cells.length;
    return {id, from: first.from, to: last.to, text: state.sliceDoc(first.from, last.to), rows, width,
        aligns: Array.from({length: width}, (_, c) => aligns[c] ?? "")};
}

/** The cell whose text holds from…to (a match of Buscar, say). */
function cellOf(table, from, to) {
    for (let row = 0; row < table.rows.length; row++) {
        const col = table.rows[row].cells.findIndex(c => c.start <= from && to <= c.end);
        if (col >= 0) return {row, col};
    }
    return null;
}

/** A table's Markdown: its header, its --- row with the alignments, and its rows. */
function tableText(rows, aligns) {
    const line = cells => "| " + cells.join(" | ") + " |";
    return [line(rows[0]), line(aligns.map(a => ALIGNS[a])), ...rows.slice(1).map(line)].join("\n");
}

/** A table's cells as the note keeps them, a row each, as many as its header has. */
const sourcesOf = table => table.rows.map(row => Array.from({length: table.width}, (_, c) => row.cells[c]?.source ?? ""));

/** A table as its grid shows it; one changed keeps its DOM, so the cell being written in keeps the focus. */
class Grid extends WidgetType {
    constructor(table, here, found) {
        super();
        this.table = table;
        this.here = here;
        this.found = found;
    }

    eq(other) {
        return other.table.id === this.table.id && other.table.text === this.table.text && other.here === this.here
            && other.found?.row === this.found?.row && other.found?.col === this.found?.col;
    }

    toDOM(view) {
        const card = element("div", "cm-table");
        card.dataset.table = this.table.id;
        const addRow = button("act", icon("plus"), words.row), addColumn = button("act", icon("plus"), words.column);
        const more = button("act more", icon("dots")), copy = button("act", icon("copy"), words.copy);
        const remove = button("act", icon("trash"), words.remove);
        more.title = words.tableMore;
        card.append(element("div", "cm-table-head", element("span", "cm-table-name", icon("table"), words.table), addRow, addColumn, more,
            element("span", "grow"), copy, remove), element("div", "cm-table-scroll", element("table", "", document.createElement("tbody"))));
        addRow.addEventListener("click", () => {
            const at = current(view, card);
            if (at) insertRow(view, at.table, at.row + 1, at.col);
        });
        addColumn.addEventListener("click", () => {
            const at = current(view, card);
            if (at) insertColumn(view, at.table, at.col + 1, at.row);
        });
        more.addEventListener("click", () => menu?.anchor === more ? closeMenu() : tableMenu(view, more, card));
        copy.addEventListener("click", () => {   // as a table for Word, Excel or Docs, and as Markdown
            const table = tableOf(view, card);
            if (!table) return;
            tell("table:" + table.text);
            say(copy, words.copied, words.copy);
        });
        remove.addEventListener("click", () => {
            const table = tableOf(view, card);
            if (!table) return;
            if (sourcesOf(table).flat().some(Boolean) && !remove.classList.contains("sure")) {   // with text in it, a second click
                remove.classList.add("sure");
                remove.querySelector(".label").textContent = words.sure;
                setTimeout(() => {
                    remove.classList.remove("sure");
                    remove.querySelector(".label").textContent = words.remove;
                }, 3000);
                return;
            }
            let {from, to} = table;
            if (to < view.state.doc.length) to++;
            else if (from > 0) from--;
            view.dispatch({changes: {from, to}, selection: {anchor: from}, scrollIntoView: true, userEvent: "input.table"});
            view.focus();
        });
        card.addEventListener("input", e => {
            if (e.isComposing) return;
            typed(view, cellIn(e));
            offerCellLinks(view, cellIn(e));
        });
        card.addEventListener("compositionend", e => typed(view, cellIn(e)));
        card.addEventListener("keydown", e => {
            cellLinkKey(e);
            cellKey(view, e);
        });
        card.addEventListener("focusout", () => setTimeout(() => {   // leaving the cell (not for its titles, which keep the focus in it)
            if (cellLinks && document.activeElement !== cellLinks.cell) closeCellLinks();
        }));
        card.addEventListener("paste", e => {
            const cell = cellIn(e);
            if (!cell) return;
            e.preventDefault();
            const text = e.clipboardData?.getData("text/plain") ?? "";
            if (!text) tell("paste");   // a picture: Java saves it and puts its link in this cell (insertText)
            else if (/[\t\n]/.test(text.replace(/\r?\n$/, ""))) pasteCells(view, cell, text);
            else insertInCell(view, cell, text);
        });
        for (const type of ["copy", "cut"]) {   // through Java, as the editor's own (see copy)
            card.addEventListener(type, e => {
                const cell = cellIn(e);
                if (!cell) return;
                e.preventDefault();
                const {start, end} = caret(cell), text = cell.textContent;
                if (start === end) return;
                tell("copy:" + text.slice(start, end));
                if (type === "cut") writeCell(view, cell, text.slice(0, start) + text.slice(end), start);
            });
        }
        // A click on the bar or on a border is nobody's: the cursor stays where it was.
        card.addEventListener("mousedown", e => {
            if (!e.target.closest("th, td, button")) e.preventDefault();
        });
        draw(card, this);
        return card;
    }

    // CodeMirror offers a widget the DOM of any other of its kind: only its own table's will do.
    updateDOM(card, view, old) {
        if (old?.table.id !== this.table.id) return false;
        draw(card, this);
        return true;
    }

    get estimatedHeight() {
        return 58 + 38 * this.table.rows.length;
    }

    ignoreEvent() {
        return true;
    }
}

/** A grid as its table is now: its rows and cells (th in the header), their texts and alignments; a cell keeps the text being written in it. */
function draw(card, {table, here, found}) {
    card.className = "cm-table" + (here ? " here-" + here : "");
    const body = card.querySelector("tbody");
    while (body.rows.length > table.rows.length) body.deleteRow(-1);
    table.rows.forEach((row, r) => {
        const tr = body.rows[r] ?? body.insertRow();
        for (let c = 0; c < table.width; c++) {
            let cell = tr.cells[c];
            if (cell?.tagName !== (r ? "TD" : "TH")) {
                const fresh = document.createElement(r ? "td" : "th");
                try {
                    fresh.contentEditable = "plaintext-only";
                } catch {
                    fresh.contentEditable = "true";
                }
                fresh.spellcheck = false;
                if (cell) cell.replaceWith(fresh);
                else tr.append(fresh);
                cell = fresh;
            }
            const source = row.cells[c]?.source ?? "";
            if (kept(cell.textContent) !== source) cell.textContent = shown(source);
            cell.style.textAlign = table.aligns[c];
            cell.classList.toggle("found", found?.row === r && found?.col === c);
        }
        while (tr.cells.length > table.width) tr.deleteCell(-1);
    });
}

let tableIds = 0;

/** The tables at the top level of the note (in a list or a quote, a table stays text), each drawn as its grid. */
function scanTables(state, before) {
    const list = [], decorations = new RangeSetBuilder(), {empty, head, from, to} = state.selection.main;
    syntaxTree(state).iterate({
        enter: node => {
            if (node.name === "Document") return true;
            if (node.name !== "Table") return false;
            const start = state.doc.lineAt(node.from).from;
            const table = readTable(state, node, before?.find(b => b.at === start)?.id ?? ++tableIds);
            list.push(table);
            const here = empty && head === table.from ? "top" : empty && head === table.to ? "bottom" : "";
            decorations.add(table.from, table.to, Decoration.replace({widget: new Grid(table, here, empty ? null : cellOf(table, from, to)), block: true}));
            return false;
        }
    });
    return {list, decorations: decorations.finish()};
}

const tables = StateField.define({
    create: state => scanTables(state, null),
    // A table keeps its id where its first line goes: the grid that shows it keeps its DOM.
    update: (value, tr) => tr.docChanged || tr.selection || syntaxTree(tr.state) !== syntaxTree(tr.startState)
        ? scanTables(tr.state, value.list.map(t => ({id: t.id, at: tr.changes.mapPos(t.from, -1)})))
        : value,
    provide: field => [
        EditorView.decorations.from(field, value => value.decorations),
        EditorView.atomicRanges.of(view => view.state.field(field).decorations),
        EditorView.editorAttributes.compute([field, "selection"], state => {
            const {empty, head} = state.selection.main;
            return {class: empty && state.field(field).list.some(t => head === t.from || head === t.to) ? "cm-beside-card" : ""};
        }),
    ],
});

const tableOf = (view, card) => view.state.field(tables).list.find(t => t.id === +card.dataset.table);

/** The cell an event of a grid happened in. */
function cellIn(e) {
    const target = e.target.nodeType === 3 ? e.target.parentElement : e.target;
    return target?.closest?.("th, td") ?? null;
}

/** Where a cell of a grid is: its table, as the note is now, and its row and column. */
function cellPlace(view, cell) {
    const card = cell?.closest(".cm-table"), table = card && tableOf(view, card);
    return table ? {table, card, row: cell.parentElement.rowIndex, col: cell.cellIndex} : null;
}

// The cell written in last, until the text around the tables gets the focus: what Java's buttons act on.
let activeCell = null;
document.addEventListener("focusin", e => {
    if (e.target.closest?.(".cm-table") && e.target.matches("th, td")) activeCell = e.target;
    else if (e.target === view.contentDOM) activeCell = null;
});
const cellNow = () => activeCell?.isConnected ? activeCell : null;

/** The cell the bar's buttons act on: the one being written in, else the last one. */
function current(view, card) {
    const table = tableOf(view, card), cell = cellNow();
    if (!table) return null;
    return cell && card.contains(cell) ? {table, row: cell.parentElement.rowIndex, col: cell.cellIndex}
        : {table, row: table.rows.length - 1, col: table.width - 1};
}

/**
 * A cell was written in: its text goes into the note's row, as typing (Ctrl+Z takes back a run of
 * it at once), with CodeMirror's own cursor after the table, where nothing reacts to it.
 */
function typed(view, cell) {
    const at = cellPlace(view, cell);
    if (!at) return;
    const {table, row, col} = at, text = kept(cell.textContent), now = table.rows[row].cells[col];
    if ((now?.source ?? "") === text) return;
    let change;
    if (!now) {   // a row with fewer cells than the header: written whole
        const line = table.rows[row].line, cells = sourcesOf(table)[row];
        cells[col] = text;
        change = {from: line.from, to: line.to, insert: "| " + cells.join(" | ") + " |"};
    } else {
        change = now.start < now.end ? {from: now.start, to: now.end, insert: text} : {from: now.from, to: now.to, insert: " " + text + " "};
    }
    view.dispatch({changes: change, selection: {anchor: table.to + change.insert.length - (change.to - change.from)}, userEvent: "input.type.table"});
}

/** Where the caret (or the selection) is in a cell's text. */
function caret(cell) {
    const sel = getSelection(), end = cell.textContent.length;
    if (!sel.rangeCount || !cell.contains(sel.getRangeAt(0).startContainer)) return {start: end, end};
    const range = sel.getRangeAt(0), before = document.createRange();
    before.selectNodeContents(cell);
    before.setEnd(range.startContainer, range.startOffset);
    const start = before.toString().length;
    return {start, end: start + range.toString().length};
}

/** Selects start…end of a cell's text (an empty range: the caret there). */
function select(cell, start, end = start) {
    const point = offset => {
        const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
        for (let node; (node = walker.nextNode());) {
            if (offset <= node.length) return [node, offset];
            offset -= node.length;
        }
        return [cell, cell.childNodes.length];
    };
    const range = document.createRange();
    range.setStart(...point(start));
    range.setEnd(...point(end));
    getSelection().removeAllRanges();
    getSelection().addRange(range);
}

/** A cell's text written anew, with the caret (or a selection) in it, as if typed. */
function writeCell(view, cell, text, start, end = start) {
    cell.textContent = text;
    select(cell, start, end);
    typed(view, cell);
}

/** Text put in a cell where its caret is, on one line (a picture's link comes from Java with a line break after it). */
function insertInCell(view, cell, text) {
    const {start, end} = caret(cell), now = cell.textContent, piece = text.replace(/^\s*\n|\n\s*$/g, "").replace(/\s*\r?\n\s*/g, " ");
    writeCell(view, cell, now.slice(0, start) + piece + now.slice(end), start + piece.length);
}

/** **bold**, *italic*, $math$ or a [[link]] around what is selected in a cell; the others are not for cells. */
function formatCell(view, cell, kind) {
    const {start, end} = caret(cell), text = cell.textContent;
    const mark = {bold: "**", italic: "*", math: "$"}[kind];
    if (kind === "link") {
        writeCell(view, cell, text.slice(0, start) + "[[" + text.slice(start, end) + "]]" + text.slice(end), end + 2);
    } else if (mark) {
        const n = mark.length, on = text.slice(start - n, start) === mark && text.slice(end, end + n) === mark
            && (mark !== "*" || text.slice(start - 2, start) !== "**");
        if (on) writeCell(view, cell, text.slice(0, start - n) + text.slice(start, end) + text.slice(end + n), start - n, end - n);
        else writeCell(view, cell, text.slice(0, start) + mark + text.slice(start, end) + mark + text.slice(end), start + n, end + n);
    }
}

/**
 * Puts the caret in a cell of a table, at the start or end of its text, selecting it all or [from, to]
 * of it; once drawn, if it is out of sight.
 */
function focusCell(view, id, row, col, where = "end", tries = 4) {
    const cell = view.contentDOM.querySelector(`.cm-table[data-table="${id}"] tbody`)?.rows[row]?.cells[col];
    if (!cell) {
        if (tries) setTimeout(() => focusCell(view, id, row, col, where, tries - 1), 50);
        else view.focus();
        return;
    }
    cell.focus();
    const n = cell.textContent.length;
    if (Array.isArray(where)) select(cell, ...where.map(p => Math.min(p, n)));
    else select(cell, where === "end" ? n : 0, where === "start" ? 0 : n);
    cell.scrollIntoView({block: "nearest"});
}

// Where Buscar leaves the cursor in a table, once the editor has the focus (not Buscar's field), the
// cell takes it, with what was found selected there: what is typed then goes in that cell.
const intoCell = EditorView.updateListener.of(u => {
    if (!(u.selectionSet || u.focusChanged) || !u.view.hasFocus) return;
    const {from, to} = u.state.selection.main, table = u.state.field(tables).list.find(t => t.from < from && to < t.to);
    const at = table && cellOf(table, from, to);
    if (!at) return;
    const cell = table.rows[at.row].cells[at.col], shownAt = p => shown(cell.source.slice(0, p - cell.start)).length;
    setTimeout(() => focusCell(u.view, table.id, at.row, at.col, [shownAt(from), shownAt(to)]));
});

/** Writes a table anew (its rows of cells as the note keeps them, its alignments), and puts the caret in one of its cells. */
function rewrite(view, table, rows, aligns, row, col) {
    view.dispatch({changes: {from: table.from, to: table.to, insert: tableText(rows, aligns)}, userEvent: "input.table"});
    focusCell(view, table.id, Math.max(0, Math.min(row, rows.length - 1)), Math.max(0, Math.min(col, aligns.length - 1)));
}

function insertRow(view, table, at, col) {
    const rows = sourcesOf(table);
    rows.splice(at, 0, Array(table.width).fill(""));
    rewrite(view, table, rows, table.aligns, at, col);
}

function insertColumn(view, table, at, row) {
    const rows = sourcesOf(table), aligns = [...table.aligns];
    rows.forEach(cells => cells.splice(at, 0, ""));
    aligns.splice(at, 0, "");
    rewrite(view, table, rows, aligns, row, at);
}

function removeRow(view, table, at, col) {
    const rows = sourcesOf(table);
    rows.splice(at, 1);
    rewrite(view, table, rows, table.aligns, at, col);
}

function removeColumn(view, table, at, row) {
    const rows = sourcesOf(table), aligns = [...table.aligns];
    rows.forEach(cells => cells.splice(at, 1));
    aligns.splice(at, 1);
    rewrite(view, table, rows, aligns, row, at);
}

function align(view, table, col, how, row) {
    const aligns = [...table.aligns];
    aligns[col] = how;
    rewrite(view, table, sourcesOf(table), aligns, row, col);
}

/** Cells pasted (from Excel, Sheets, SQL Developer…) into a table, from the cell with the caret on; rows and columns are added if they do not fit. */
function pasteCells(view, cell, text) {
    const at = cellPlace(view, cell);
    if (!at) return;
    const {table, row, col} = at, grid = text.replace(/\r\n?/g, "\n").replace(/\n$/, "").split("\n").map(line => line.split("\t"));
    const width = Math.max(table.width, col + Math.max(...grid.map(values => values.length)));
    const rows = sourcesOf(table).map(cells => [...cells, ...Array(width - table.width).fill("")]);
    grid.forEach((values, i) => {
        while (rows.length <= row + i) rows.push(Array(width).fill(""));
        values.forEach((value, j) => rows[row + i][col + j] = kept(value));
    });
    rewrite(view, table, rows, [...table.aligns, ...Array(width - table.width).fill("")], row + grid.length - 1, col + grid.at(-1).length - 1);
}

/** The cursor out of a table, in the text right above or below it. */
function leaveTable(view, table, below) {
    const {length} = view.state.doc;
    view.focus();
    view.dispatch({selection: {anchor: below ? Math.min(table.to + 1, length) : Math.max(table.from - 1, 0)}, scrollIntoView: true});
}

/** Whether the caret is on the first (or last) line of its cell's text, which may wrap. */
function edgeLine(cell, last) {
    const sel = getSelection(), whole = document.createRange();
    if (!sel.rangeCount || !cell.textContent) return true;
    whole.selectNodeContents(cell);
    const lines = whole.getClientRects(), at = sel.getRangeAt(0).getClientRects()[0];
    if (!lines.length || !at) return true;
    return last ? at.bottom >= lines[lines.length - 1].bottom - 2 : at.top <= lines[0].top + 2;
}

/**
 * The keys in a cell, as in a spreadsheet: Tab and Shift+Tab to the next and the previous cell, Enter
 * to the one below (adding a row after the last; on a new empty row, out of the table, as in a
 * list), the arrows from cell to cell and out of the table at its edges, Esc out of it. Ctrl+Z and
 * Ctrl+Y are the note's, and Ctrl+B and Ctrl+I mark what is selected.
 */
function cellKey(view, e) {
    const cell = cellIn(e), at = !e.isComposing && !e.defaultPrevented && cellPlace(view, cell);   // Esc that closed a menu is the menu's
    if (!at) return;
    const {table, row, col} = at, last = table.rows.length - 1, mod = e.ctrlKey || e.metaKey;
    const go = (r, c, where = "end") => focusCell(view, table.id, r, c, where);
    const {start, end} = caret(cell), size = cell.textContent.length, plain = !mod && !e.altKey;
    if (e.key === "Tab" && plain) {
        if (e.shiftKey) {
            if (col > 0) go(row, col - 1, "all");
            else if (row > 0) go(row - 1, table.width - 1, "all");
        } else if (col < table.width - 1) go(row, col + 1, "all");
        else if (row < last) go(row + 1, 0, "all");
        else insertRow(view, table, row + 1, 0);
    } else if (e.key === "Enter" && plain) {
        if (e.shiftKey) {
            if (row > 0) go(row - 1, col);
        } else if (row < last) {
            go(row + 1, col);
        } else if (row > 1 && !sourcesOf(table)[row].some(Boolean)) {   // a new row left empty: out of the table
            const line = table.rows[row].line;
            view.focus();
            view.dispatch({changes: {from: line.from, to: line.to}, selection: {anchor: line.from}, scrollIntoView: true, userEvent: "input.table"});
        } else {
            insertRow(view, table, row + 1, col);
        }
    } else if ((e.key === "ArrowDown" || e.key === "ArrowUp") && plain && !e.shiftKey && edgeLine(cell, e.key === "ArrowDown")) {
        const r = row + (e.key === "ArrowDown" ? 1 : -1);
        if (r < 0 || r > last) leaveTable(view, table, r > last);
        else go(r, col);
    } else if (e.key === "ArrowLeft" && plain && !e.shiftKey && start === 0 && end === 0) {
        if (col > 0) go(row, col - 1);
        else if (row > 0) go(row - 1, table.width - 1);
        else leaveTable(view, table, false);
    } else if (e.key === "ArrowRight" && plain && !e.shiftKey && start === size && end === size) {
        if (col < table.width - 1) go(row, col + 1, "start");
        else if (row < last) go(row + 1, 0, "start");
        else leaveTable(view, table, true);
    } else if (e.key === "Escape") {
        leaveTable(view, table, true);
    } else if (mod && !e.altKey && (e.key === "z" || e.key === "Z" || e.key === "y")) {
        (e.key === "z" ? undo : redo)(view);
        const now = tableOf(view, at.card);   // the same cell, as far as the table reaches now
        if (now) go(Math.min(row, now.rows.length - 1), Math.min(col, now.width - 1));
        else view.focus();
    } else if (mod && !e.altKey && !e.shiftKey && (e.key === "b" || e.key === "i")) {
        formatCell(view, cell, e.key === "b" ? "bold" : "italic");
    } else {
        return;
    }
    e.preventDefault();
}

// What [[ offers while writing, in a cell too: under the caret, the titles that hold what follows the [[;
// ↑ and ↓ choose one, Enter or Tab take it, Esc closes them.
let cellLinks = null;

function closeCellLinks() {
    cellLinks?.box.remove();
    cellLinks = null;
}

/** After typing in a cell: when its caret follows a [[ not closed yet, the titles that fit what follows it. */
function offerCellLinks(view, cell) {
    const {start, end} = cell ? caret(cell) : {};
    const typed = cell && start === end ? /\[\[([^\]|\n]*)$/.exec(cell.textContent.slice(0, start)) : null;
    const wanted = typed && fold(typed[1].trim());
    const found = typed ? titles.filter(t => fold(t).includes(wanted)).sort((a, b) => fold(b).startsWith(wanted) - fold(a).startsWith(wanted)).slice(0, 30) : [];
    if (!found.length) return closeCellLinks();
    if (cellLinks?.cell !== cell) {
        closeCellLinks();
        cellLinks = {cell, view, box: element("div", "lang-menu cell-links")};
        document.body.append(cellLinks.box);
    }
    Object.assign(cellLinks, {from: start - typed[1].length, found, at: 0});
    cellLinks.box.replaceChildren(...found.map((title, i) => option(null, title, "", () => takeCellLink(i))));
    markCellLink();
    const r = getSelection().getRangeAt(0).getBoundingClientRect(), at = r.height ? r : cell.getBoundingClientRect(), box = cellLinks.box;
    const down = at.bottom + 4 + box.offsetHeight <= window.innerHeight || at.top < box.offsetHeight + 8;
    box.style.left = Math.max(8, Math.min(at.left, window.innerWidth - box.offsetWidth - 8)) + "px";
    box.style.top = (down ? at.bottom + 4 : at.top - 4 - box.offsetHeight) + "px";
}

function markCellLink() {
    [...cellLinks.box.children].forEach((o, i) => o.classList.toggle("current", i === cellLinks.at));
    cellLinks.box.children[cellLinks.at]?.scrollIntoView({block: "nearest"});
}

/** One of the titles, written as the cell's [[link]], with the caret after it. */
function takeCellLink(i) {
    const {cell, view, from, found} = cellLinks, title = found[i], text = cell.textContent, {start} = caret(cell);
    const closing = text.slice(start, start + 2) === "]]" ? "" : "]]";
    closeCellLinks();
    writeCell(view, cell, text.slice(0, from) + title + closing + text.slice(start), from + title.length + 2);
}

/** The keys of the titles under a cell's caret, before the cell's own (cellKey, which leaves a key already taken). */
function cellLinkKey(e) {
    if (!cellLinks || e.isComposing || e.ctrlKey || e.metaKey || e.altKey) return;
    const n = cellLinks.found.length;
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        cellLinks.at = (cellLinks.at + (e.key === "ArrowDown" ? 1 : n - 1)) % n;
        markCellLink();
    } else if (e.key === "Enter" || e.key === "Tab") {
        takeCellLink(cellLinks.at);
    } else if (e.key === "Escape") {
        closeCellLinks();
    } else {
        return;
    }
    e.preventDefault();
}

document.addEventListener("mousedown", e => {
    if (cellLinks && !cellLinks.box.contains(e.target)) closeCellLinks();
}, true);

/** What the ⋯ of a table's bar offers, for the cell being written in (else the last one). */
function tableMenu(view, chip, card) {
    closeMenu();
    const at = current(view, card);
    if (!at) return;
    const {table, row, col} = at, head = shown(table.rows[0].cells[col]?.source ?? "");
    const name = head ? `«${head.length > 24 ? head.slice(0, 23) + "…" : head}»` : String(col + 1);
    const act = f => () => {
        closeMenu();
        f();
    };
    menu = element("div", "lang-menu table-menu");
    menu.anchor = chip;
    menu.append(option(null, words.rowAbove, "", act(() => insertRow(view, table, row, col))),
        option(null, words.columnLeft, "", act(() => insertColumn(view, table, col, row))));
    if (table.rows.length > 1 || table.width > 1) menu.append(element("hr", ""));
    if (table.rows.length > 1) {
        menu.append(option(null, row ? `Quitar la fila ${row}` : words.removeHeader, "", act(() => removeRow(view, table, row, col))));
    }
    if (table.width > 1) menu.append(option(null, `Quitar la columna ${name}`, "", act(() => removeColumn(view, table, col, row))));
    menu.append(element("hr", ""));
    for (const how of ["left", "center", "right"]) {
        const now = (table.aligns[col] || "left") === how;
        menu.append(option(null, words[how], now ? words.current : "", act(() => align(view, table, col, how, row)), now));
    }
    document.body.append(menu);
    place(chip);
}

/** Where a new table goes at pos: on lines of its own (after the code block or the table pos is in), an empty line between it and any text. */
function placeTable(state, pos, text) {
    const {doc} = state, inside = [...state.field(cards).list.map(c => ({from: c.open.from, to: c.close.to})), ...state.field(tables).list]
        .find(b => b.from < pos && pos < b.to);
    if (inside) pos = inside.to;
    const line = doc.lineAt(pos), blank = n => n < 1 || n > doc.lines || !doc.line(n).text.trim();
    let from = line.to, before = "\n\n", after = blank(line.number + 1) ? "" : "\n";
    if (!line.text.trim()) [from, before] = [line.from, blank(line.number - 1) ? "" : "\n"];
    else if (pos === line.from) [from, before, after] = [line.from, blank(line.number - 1) ? "" : "\n", "\n\n"];
    const insert = before + text + after;
    return {from, to: !line.text.trim() ? line.to : from, insert, start: from + before.length};
}

/** A new table at the cursor (after the table being written in), with the caret in its first cell. */
function newTable(view, columns, rows) {
    const cell = cellNow(), inTable = cell && cellPlace(view, cell)?.table;
    const text = tableText(Array.from({length: rows}, () => Array(columns).fill("")), Array(columns).fill(""));
    const at = placeTable(view.state, inTable ? inTable.to : view.state.selection.main.head, text);
    view.dispatch({changes: at, effects: EditorView.scrollIntoView(at.start, {y: "nearest"}), userEvent: "input.table"});
    const table = view.state.field(tables).list.find(t => t.from === at.start);
    if (table) focusCell(view, table.id, 0, 0);
}

/** Cells copied from Excel, Sheets, SQL Developer or a page: every line with as many tabs, more than one line, and no code's indentation. */
function cellsIn(text) {
    const lines = text.replace(/\r\n?/g, "\n").replace(/\n+$/, "").split("\n"), n = lines[0].split("\t").length;
    if (lines.length < 2 || n < 2 || lines.some(l => l.split("\t").length !== n) || lines.every(l => l.startsWith("\t"))) return null;
    return lines.map(l => l.split("\t").map(kept));
}

/**
 * Cells pasted outside a table become one, the first row its header. The paste itself comes first:
 * Ctrl+Z turns the table back into what was pasted.
 */
function pasteTable(view, text, grid) {
    const {from, to} = view.state.selection.main, before = view.state.update({changes: {from, to}}).state;
    const width = Math.max(...grid.map(r => r.length)), rows = grid.map(r => [...r, ...Array(width - r.length).fill("")]);
    const table = tableText(rows, Array(width).fill("")), at = placeTable(before, from, table), length = before.doc.length;
    view.dispatch(view.state.replaceSelection(text), {userEvent: "input.table.paste", scrollIntoView: true});
    // from the note with the paste to the note with the table instead
    const changes = ChangeSet.of({from, insert: text}, length).invert(before.doc).compose(ChangeSet.of(at, length));
    const after = at.start + table.length + 1;
    view.dispatch({changes, selection: {anchor: Math.min(after, changes.newLength)}, annotations: isolateHistory.of("before"),
        userEvent: "input.table", scrollIntoView: true});
}

/** Into a table from the text beside it: its first or last row, at the column under x (else its first or last cell). */
function enterTable(view, table, last, x, where) {
    const card = view.contentDOM.querySelector(`.cm-table[data-table="${table.id}"]`), row = last ? table.rows.length - 1 : 0;
    const cells = card ? [...card.querySelector("tbody").rows[row].cells] : [];
    const col = x == null ? (last ? table.width - 1 : 0) : Math.max(0, cells.findIndex(c => c.getBoundingClientRect().right >= x));
    focusCell(view, table.id, row, col, where);
    return true;
}

/** ↓ and ↑ go into a table from the text above or below it, to its nearest cell; → and ← from right before or after it. */
function arrowIntoTable(view, key) {
    const {state} = view, range = state.selection.main, list = state.field(tables).list;
    if (!range.empty || !list.length) return false;
    const {head} = range;
    if (key === "right") {
        const t = list.find(t => head === t.from || head === t.from - 1);
        return t ? enterTable(view, t, false, null, "start") : false;
    }
    if (key === "left") {
        const t = list.find(t => head === t.to || head === t.to + 1);
        return t ? enterTable(view, t, true, null, "end") : false;
    }
    const down = key === "down", next = view.moveVertically(range, down).head, x = view.coordsAtPos(head)?.left;
    const t = list.find(t => down ? head === t.from || head < t.from && next >= t.from : head === t.to || head > t.to && next <= t.to);
    return t ? enterTable(view, t, !down, x, "end") : false;
}

/** Backspace at the start of a line right after a table, or Delete at the end of one right before it: into the table, not its Markdown. */
function deleteIntoTable(view, forward) {
    const {state} = view, {empty, head} = state.selection.main, filled = () => state.doc.lineAt(head).text.trim();
    if (!empty) return false;
    const t = state.field(tables).list.find(t => forward ? head === t.from || head === t.from - 1 && filled()
        : head === t.to || head === t.to + 1 && filled());
    return t ? enterTable(view, t, !forward, null, forward ? "start" : "end") : false;
}

/**
 * Enter after a line like | Nombre | Edad |: a table, with that line as its header and a row to
 * write in, the caret in its first cell.
 */
function startTable(view) {
    const {state} = view, {head, empty} = state.selection.main, line = state.doc.lineAt(head);
    if (!empty || head !== line.to || !/^\s*\|.*\|\s*$/.test(line.text) || inCode(state, head)) return false;
    const cells = rowCells(line.text), next = line.number < state.doc.lines ? state.doc.line(line.number + 1).text : "";
    if (cells.length < 2 || /^\s*\|?\s*:?-+/.test(next) || state.field(tables).list.some(t => t.from <= head && head <= t.to)) return false;
    for (let n = syntaxTree(state).resolveInner(line.from, 1); n; n = n.parent) if (n.name === "ListItem" || n.name === "Blockquote") return false;
    const header = cells.map(c => line.text.slice(c.start, c.end));
    const text = tableText([header, Array(header.length).fill("")], Array(header.length).fill(""));
    const at = placeTable(state.update({changes: {from: line.from, to: line.to}}).state, line.from, text);   // in place of the line, now empty
    view.dispatch({changes: {from: line.from, to: line.to, insert: at.insert}, userEvent: "input.table", scrollIntoView: true});
    const table = view.state.field(tables).list.find(t => t.from === at.start);
    if (table) focusCell(view, table.id, 1, 0);
    return true;
}

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
    if (!tr.docChanged || tr.isUserEvent("input.table") || tr.isUserEvent("input.type.table")) return tr;   // a table's own
    if (!["input", "delete", "move"].some(e => tr.isUserEvent(e))) return tr;
    const list = tr.startState.field(cards).list, grids = tr.startState.field(tables).list;
    if (!list.length && !grids.length) return tr;
    const changes = [];
    tr.changes.iterChanges((fromA, toA, fromB, toB, text) => changes.push({fromA, toA, text: text.toString()}));
    const userEvent = tr.annotation(Transaction.userEvent), start = tr.startState.selection.main;
    const insert = (at, text, cursor) => ({changes: {from: at, insert: text}, selection: {anchor: cursor}, scrollIntoView: true, userEvent});
    if (changes.length === 1 && changes[0].fromA === changes[0].toA) {   // typed or pasted at one place
        const {fromA: at, text} = changes[0];
        for (const t of grids) {   // right before or after a table, on a line of its own, as beside a card
            if (at === t.from) return insert(at, text.endsWith("\n") ? text : text + "\n", at + text.length);
            if (at === t.to) return insert(at, text.startsWith("\n") ? text : "\n" + text, at + (text.startsWith("\n") ? 0 : 1) + text.length);
            if (t.from < at && at < t.to && /[\n|]/.test(text)) return [];   // in a table (where Buscar left the cursor): it would split it
        }
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
    if (broken) {
        if (changes.length === 1 && tr.isUserEvent("delete") && start.empty) {   // Backspace or Delete beside a fence
            if (!codeOf(tr.startState, broken).trim()) return removal(tr.startState, broken);
            if (start.head <= broken.open.from) return {selection: {anchor: broken.open.to + 1}, scrollIntoView: true};
            if (start.head >= broken.close.to) return {selection: {anchor: broken.close.from - 1}, scrollIntoView: true};
        }
        return [];
    }
    const table = grids.find(t => breaksTable(tr, t));
    if (!table) return tr;
    if (changes.length === 1 && tr.isUserEvent("delete") && start.empty) {   // Backspace right before a table, Delete right after it: past its line
        if (start.head === table.from) return {selection: {anchor: table.from - 1}, scrollIntoView: true};
        if (start.head === table.to) return {selection: {anchor: table.to + 1}, scrollIntoView: true};
    }
    return [];
});

/**
 * Whether a change leaves a table's lines anything but whole and as they were, unless it removes the
 * table whole, or only changes the text of its cells (what Buscar found in one, typed over).
 */
function breaksTable(tr, table) {
    let whole = false, touched = false, cells = true;
    tr.changes.iterChanges((fromA, toA, fromB, toB, text) => {
        whole ||= fromA <= table.from && toA >= table.to;
        touched ||= fromA <= table.to && toA >= table.from;
        cells &&= !/[\n|]/.test(text.toString()) && table.rows.some(row => row.cells.some(c => c.start <= fromA && toA <= c.end));
    });
    if (whole || !touched || cells) return false;
    const doc = tr.newDoc, from = tr.changes.mapPos(table.from, 1), to = tr.changes.mapPos(table.to, -1);
    return doc.lineAt(from).from !== from || doc.lineAt(to).to !== to || doc.sliceString(from, to) !== table.text;
}

/** Where a block of lines goes: in place of the empty line at pos, else after its line (before a card or a table it starts at, after a table it is in). */
function ownLines(state, pos, block) {
    pos = state.field(tables).list.find(t => t.from < pos && pos <= t.to)?.to ?? pos;
    const line = state.doc.lineAt(pos), last = line.to === state.doc.length ? "\n" : "";
    const before = [...state.field(cards).list.map(c => c.open.from), ...state.field(tables).list.map(t => t.from)].includes(pos);
    const [from, to, insert] = before ? [pos, pos, block + "\n"]
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
        option(icon("diagram"), words.diagramLong, now.diagram ? words.current : "", () => pick(DIAGRAM), now.diagram),
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
    if (id !== OUTPUT && id !== DIAGRAM) tell("language:" + id);   // the language of the notebook's next blocks
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
    paste(e, view) {
        const text = e.clipboardData?.getData("text/plain");
        if (text) {   // text: CodeMirror pastes it; cells from a spreadsheet make a table
            const grid = !inCode(view.state, view.state.selection.main.head) && cellsIn(text);
            if (!grid) return false;
            e.preventDefault();
            pasteTable(view, text, grid);
            return true;
        }
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
    cards, tables, intoCell, guard, lineUp, drawingLinks, callouts, backlinkField, formulaPreview, pictures,
    Prec.high(keymap.of([
        {key: "Enter", run: view => closeFence(view) || startTable(view)},
        {key: "Mod-b", run: view => format(view, "bold")},
        {key: "Mod-i", run: view => format(view, "italic")},
        {key: "ArrowDown", run: view => arrowIntoTable(view, "down")},
        {key: "ArrowUp", run: view => arrowIntoTable(view, "up")},
        {key: "ArrowRight", run: view => arrowIntoTable(view, "right")},
        {key: "ArrowLeft", run: view => arrowIntoTable(view, "left")},
        {key: "Backspace", run: view => deleteIntoTable(view, false)},
        {key: "Delete", run: view => deleteIntoTable(view, true)},
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
view.scrollDOM.addEventListener("scroll", () => {
    closeMenu();
    closeCellLinks();
});
window.editorView = view;   // for the tests: where CodeMirror puts each line, and where a click lands

/** Accents and case do not matter when searching, and a folded text keeps the positions of the original. */
function fold(text) {
    let out = "";
    for (let i = 0; i < text.length; i++) out += text[i].normalize("NFD").replace(/\p{M}/gu, "").toLowerCase()[0] ?? text[i];
    return out;
}

// Called by Java. What Java types goes in as typing does ("input.api"), beside cards too.
window.setText = (text, base) => {   // the note, and its folder's URL (its pictures are there)
    closeMenu();
    closeCellLinks();
    backlinks = [];   // until Java finds this note's
    noteBase = base ?? "";
    pictureStamp = Date.now();
    view.setState(EditorState.create({doc: text, extensions: extensions()}));
};
window.refreshPictures = () => {   // a drawing of the note was drawn on again: its pictures load anew
    pictureStamp = Date.now();
    view.dispatch({effects: refreshPictures.of(null)});
};
window.setBacklinks = lines => {
    backlinks = lines ? lines.split("\n").map(line => ({title: line.split("\t")[0], where: line.split("\t")[1] ?? ""})) : [];
    view.dispatch({effects: showBacklinks.of(backlinks)});
    readingBacklinks();
};
window.getText = () => view.state.doc.toString();
window.setTitles = lines => titles = lines ? lines.split("\n") : [];
window.setTheme = theme => {
    document.documentElement.dataset.theme = theme;
    redrawDiagrams();   // their colors are in their drawings
};
window.setCodeLanguage = id => codeLanguage = id ?? "";
// Java's text, buttons and blocks go in the cell being written in, or after its table.
window.insertText = text => {
    const cell = cellNow();
    if (cell) insertInCell(view, cell, text);
    else view.dispatch(view.state.replaceSelection(text), {scrollIntoView: true, userEvent: "input.api"});
};
/** Where the cursor is for Java: after the table of the cell being written in. */
const cursorAt = () => cellPlace(view, cellNow())?.table.to ?? view.state.selection.main.head;
window.insertCode = (code, lang) => {   // as it is inside a code block; in a new block outside one
    const {state} = view, head = cursorAt(), list = state.field(cards).list;
    const inCard = !cellNow() && list.some(c => c.open.to < head && head < c.close.from);
    const beside = list.some(c => c.open.from === head || c.close.to === head);
    if (inCard || (!cellNow() && !beside && inCode(state, head))) view.dispatch(state.replaceSelection(code), {scrollIntoView: true, userEvent: "input.api"});
    else newBlock(view, head, code, lang);
    view.focus();
};
window.newBlock = (code = "", lang = codeLanguage) => {   // after the card with the cursor, or where the cursor is
    const head = cursorAt(), card = view.state.field(cards).list.find(c => c.open.from <= head && head <= c.close.to);
    newBlock(view, card ? card.close.to : head, code, lang);
};
window.insertDiagram = code => window.newBlock(code, DIAGRAM);
// A callout ([!definition]…): with lines selected, around them; else a new one to write in, after the block (code,
// table or quote) with the cursor, or where it is. A quote right before or after it would join it: a blank line between.
const quoteLine = line => line != null && /^\s*>/.test(line.text);
window.insertCallout = type => {
    if (cellNow()) return;
    const {state} = view, {from, to, empty} = state.selection.main, mark = `> [!${type}]`;
    let change, caret;
    if (!empty) {
        const first = state.doc.lineAt(from), last = state.doc.lineAt(to);
        const box = mark + "\n" + state.sliceDoc(first.from, last.to).split("\n").map(line => "> " + line).join("\n");
        const before = first.number > 1 && quoteLine(state.doc.line(first.number - 1)) ? "\n" : "";
        const after = last.number < state.doc.lines && quoteLine(state.doc.line(last.number + 1)) ? "\n" : "";
        change = {from: first.from, to: last.to, insert: before + box + after};
        caret = first.from + before.length + box.length;
    } else {
        let at = cursorAt();
        const card = state.field(cards).list.find(c => c.open.from <= at && at <= c.close.to);
        if (card) at = card.close.to;
        for (let n = syntaxTree(state).resolveInner(at, -1); n; n = n.parent) if (n.name === "Blockquote") at = n.to;
        const box = mark + "\n> ", place = ownLines(state, at, box);
        const prev = place.insert.startsWith("\n") ? state.doc.lineAt(place.from) : place.from > 0 ? state.doc.lineAt(place.from - 1) : null;
        const next = place.to < state.doc.length ? state.doc.lineAt(place.to + 1) : null;
        const insert = place.insert.replace(box, (quoteLine(prev) ? "\n" : "") + box + (quoteLine(next) ? "\n" : ""));
        change = {from: place.from, to: place.to, insert};
        caret = place.from + insert.indexOf(box) + box.length;
    }
    view.dispatch({changes: change, selection: {anchor: caret}, scrollIntoView: true, userEvent: "input.callout"});
    view.focus();
};
window.format = kind => {
    const cell = cellNow();
    if (cell) formatCell(view, cell, kind);
    else format(view, kind);
};
window.insertTable = (columns, rows) => newTable(view, columns, rows);
window.selectMatch = text => {
    const at = fold(view.state.doc.toString()).indexOf(fold(text));
    if (at < 0) return;
    view.dispatch({selection: {anchor: at, head: at + text.length}, scrollIntoView: true});
    view.focus();
};
window.focusEditor = () => {
    const cell = cellNow();
    if (cell) cell.focus();
    else view.focus();
};
window.showEditor = () => {
    reading.hidden = true;
    editor.hidden = false;
    view.focus();
};
window.showReading = html => {
    closeMenu();
    render(reading, html, dark());
    reading.querySelectorAll("img.drawing").forEach(img => img.title = words.drawHint);
    readingBacklinks();
    editor.hidden = true;
    reading.hidden = false;
    reading.scrollTop = 0;
};

// The same for paper (a PDF, a print), made apart from the page: Java gets it once its code has its colors, its
// drawings their size and its diagrams their drawings, in the light theme. Nothing on paper is clicked: no Copy
// buttons, and a [[link]] leads nowhere.
window.printable = html => {
    const page = document.createElement("article");
    Promise.allSettled(render(page, html, false)).then(() => {
        page.querySelectorAll(".copy").forEach(copy => copy.remove());
        page.querySelectorAll(".wikilink").forEach(link => link.removeAttribute("href"));
        page.querySelectorAll("[data-code]").forEach(box => box.removeAttribute("data-code"));
        tell("print:" + page.innerHTML);
    });
};

// A note's HTML (from Java) into an element: its formulas, its callouts, its code as cards, its diagrams drawn (in
// the dark theme or the light one), and its pictures and drawings at their size. What is still on its way, as
// promises: the colors of the code, the diagrams, the drawings loading.
function render(into, html, inDark) {
    into.innerHTML = html;   // commonmark-java already turned any HTML written in the note into text
    into.querySelectorAll(".math").forEach(math => katex.render(math.textContent, math,
        {displayMode: math.classList.contains("display"), throwOnError: false}));
    into.querySelectorAll("blockquote").forEach(callout);
    const coming = [...into.querySelectorAll("pre > code")].map(code =>
        code.className.toLowerCase() === "language-" + DIAGRAM ? diagram(code, inDark) : card(code));
    into.querySelectorAll("img").forEach(img => {
        const sized = SIZED.exec(img.getAttribute("alt") ?? "");   // ![|400](…): its own width, in pixels (and height)
        if (sized) {
            img.alt = img.alt.replace(SIZED, "");
            img.style.width = sized[1] + "px";
            if (sized[2]) img.style.height = sized[2] + "px";
        }
        if (!/\.excalidraw\.png(\?|$)/i.test(img.getAttribute("src"))) return;
        img.classList.add("drawing");
        if (sized) return;
        coming.push(new Promise(done => {
            const drawn = () => {
                if (img.naturalWidth) img.style.width = img.naturalWidth / 2 + "px";   // its PNG is twice as big, to look sharp
                done();
            };
            if (img.complete) drawn();
            else {
                img.addEventListener("load", drawn);
                img.addEventListener("error", done);
            }
        }));
    });
    return coming;
}

// In the reading view a [[link]] opens its note, a #tag shows its notes, and any other link opens
// in the system browser, never inside this page.
reading.addEventListener("click", e => {
    const note = e.target.closest(".wikilink"), tag = e.target.closest(".tag"), link = e.target.closest("a[href]");
    const drawing = e.target.closest("img.drawing");
    if (drawing) tell("draw:" + drawing.getAttribute("src"));
    else if (note) tell("open:" + note.dataset.note);
    else if (tag) tell("tag:" + tag.dataset.tag);
    else if (link) tell("link:" + link.href);
    if (note || link) e.preventDefault();
});

tell("ready");

// A diagram of the reading view: drawn; or, when its code has an error, what is wrong above its code.
function diagram(code, inDark) {
    const pre = code.parentElement, text = code.textContent.replace(/\n$/, "");
    if (!text.trim()) {
        pre.remove();
        return;
    }
    const box = element("div", "diagram");
    box.dataset.code = text;
    pre.replaceWith(box);
    return drawDiagram(box, inDark).then(() => {
        if (!box.classList.contains("broken")) return;
        box.removeAttribute("data-code");   // stays as it is when the theme changes: its code is a card
        box.append(pre);
        return card(code);
    });
}

// A code block of the reading view becomes a card as while writing: its language and a Copy button
// above numbered lines with the same colors (once its language has loaded: the promise). A block of
// output is plain text.
function card(code) {
    const info = code.className.startsWith("language-") ? code.className.slice("language-".length) : "";
    const kind = describe(info), text = code.textContent.replace(/\n$/, "");
    const copy = element("button", "copy", words.copy);
    copy.addEventListener("click", () => {
        tell("copy:" + text);
        say(copy, words.copied, words.copy);
    });
    const label = kind.unset ? element("span", "lang", words.code)
        : element("span", "lang", mark(kind), kind.name);
    const box = element("div", "code-card" + (kind.output ? " output" : "")), pre = code.parentElement;
    pre.replaceWith(box);
    box.append(element("div", "code-head", label, copy), pre);
    if (kind.output) return;
    lines(code, text, null);
    return language(info)?.load().then(support => lines(code, text, support.language.parser.parse(text)));
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
