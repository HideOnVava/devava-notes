// Before Excalidraw loads.
import "./keys.js";

// Its fonts are next to the page (fonts/), and as woff: JavaFX's WebView reads no woff2, so
// build-draw.mjs converts them and Excalidraw's faces are pointed at the copies. And JavaFX's WebKit,
// as Safari, never tells when a face has loaded (FontFaceSet's loadingdone), which is when Excalidraw
// draws its texts again in it: each face tells it here, or they would stay in a font of the system.
window.EXCALIDRAW_ASSET_PATH = new URL("./", location.href).href;

const NativeFontFace = window.FontFace;
window.FontFace = function (family, source, descriptors) {
    if (typeof source === "string") source = source.replace(/\.woff2(?=['")])/g, ".woff").replace(/format\((['"])woff2\1\)/g, "format('woff')");
    const face = new NativeFontFace(family, source, descriptors);
    face.loaded.then(() => {
        const done = new Event("loadingdone");
        done.fontfaces = [face];
        document.fonts.dispatchEvent(done);
    }, () => {});   // a font left out of the app (build-draw.mjs): the system's draws those texts
    return face;
};
window.FontFace.prototype = NativeFontFace.prototype;

// The clipboard goes through Java, as in the editor (main.js): JavaFX's WebKit has no clipboard API,
// and empties the clipboard when a page writes to it. Excalidraw copies with writeText, which hands
// the text to Java; its menu's Pegar reads with readText, which asks Java (prompt) for the text, and
// Java answers it, or pastes the clipboard's pictures itself and answers nothing. Ctrl+V of pictures
// (a screenshot, image files) asks Java too, since the page cannot read them.
const clipboard = {
    writeText: async text => window.alert("copy:" + text),
    readText: async () => {
        const text = window.prompt("clipboard");
        if (text) return text;
        throw new DOMException("Java pasted the pictures, or there was nothing to paste", "AbortError");   // which Excalidraw keeps quiet about
    },
};
Object.defineProperty(navigator, "clipboard", {value: clipboard, configurable: true});
window.addEventListener("paste", e => {
    if (!e.clipboardData?.getData("text/plain")) window.alert("paste");
}, true);
