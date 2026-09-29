// KaTeX's stylesheet and fonts go next to the bundle, where editor.html links katex/katex.min.css.
// Only the woff fonts: JavaFX's WebView cannot read woff2 (tested: those faces end in "error"),
// and woff is the next format the stylesheet names.
import {copyFileSync, mkdirSync, readdirSync, rmSync} from "node:fs";

const from = "node_modules/katex/dist", out = "../src/main/resources/com/devavaxp/notes/katex";
rmSync(out, {recursive: true, force: true});
mkdirSync(out + "/fonts", {recursive: true});
for (const font of readdirSync(from + "/fonts").filter(name => name.endsWith(".woff"))) {
    copyFileSync(`${from}/fonts/${font}`, `${out}/fonts/${font}`);
}
copyFileSync(from + "/katex.min.css", out + "/katex.min.css");
