// Builds the drawing window's page next to editor.html: draw.js and draw.css (Excalidraw, React and
// draw-setup.js, in Spanish only), and Excalidraw's fonts in fonts/, as woff: JavaFX's WebView
// cannot read woff2 (see copy-katex.mjs), so each one is decompressed and compressed again as woff,
// under the same name (draw-setup.js and draw.css point at the copies). Two stay out: Xiaolai, its
// font for Chinese, Japanese and Korean, 13 MB the notes of the Tec do not need; and Liberation Sans,
// which only stands in for Helvetica in old drawings (Windows draws Arial, of the same measures), and
// whose version 1 is under the GPL.
import {build} from "esbuild";
import {mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync} from "node:fs";
import {dirname, join} from "node:path";
import {pathToFileURL} from "node:url";
import {decompress} from "wawoff2";
import ttf2woff from "ttf2woff";

const out = "../src/main/resources/com/devavaxp/notes";

// Excalidraw loads each language when asked for it: all but Spanish become empty modules, and its
// Spanish gets what its translation lacks, and a clearer word or two (draw-es.json): nothing of the
// window may be in English. The build fails when an update of Excalidraw brings words to translate.
const load = async file => (await import(pathToFileURL(file).href)).default;
const merge = (into, from) => {
    for (const [key, value] of Object.entries(from)) into[key] = typeof value === "object" ? merge(into[key] ?? {}, value) : value;
    return into;
};
const lacking = (english, spanish, at = "") => Object.entries(english).flatMap(([key, value]) =>
    typeof value === "object" ? lacking(value, spanish?.[key], `${at}${key}.`) : spanish?.[key] ? [] : [at + key]);
const spanishOnly = {
    name: "spanish-only",
    setup(b) {
        b.onResolve({filter: /^\.\/locales\/[a-z]{2,3}-[A-Za-z]{2,4}-[A-Z0-9]+\.js$/}, args =>
            ({path: join(args.resolveDir, args.path), namespace: args.path.includes("/es-ES-") ? "spanish" : "no-locale"}));
        b.onLoad({filter: /.*/, namespace: "no-locale"}, () => ({contents: "export default {};"}));
        b.onLoad({filter: /.*/, namespace: "spanish"}, async args => {
            const locales = dirname(args.path);
            const english = await load(join(locales, readdirSync(locales).find(name => name.startsWith("en-"))));
            const spanish = merge(await load(args.path), JSON.parse(readFileSync("draw-es.json", "utf8")));
            const missing = lacking(english, spanish);
            if (missing.length) throw new Error(`draw-es.json lacks these words of Excalidraw: ${missing.join(", ")}`);
            return {contents: JSON.stringify(spanish), loader: "json"};   // its sections, as the names Excalidraw reads
        });
    },
};

await build({
    entryPoints: ["draw.js"], outfile: `${out}/draw.js`, bundle: true, minify: true, format: "iife", target: "safari16",
    conditions: ["production"], define: {"process.env.NODE_ENV": '"production"'}, external: ["*.woff2"], plugins: [spanishOnly],
    logLevel: "warning",
});
const css = `${out}/draw.css`;
writeFileSync(css, readFileSync(css, "utf8").replace(/\.woff2\)\s*format\((["'])woff2\1\)/g, '.woff) format("woff")'));

const from = "node_modules/@excalidraw/excalidraw/dist/prod/fonts", fonts = `${out}/fonts`;
rmSync(fonts, {recursive: true, force: true});
for (const family of readdirSync(from).filter(name => !["Xiaolai", "Liberation"].includes(name))) {
    mkdirSync(`${fonts}/${family}`, {recursive: true});
    for (const font of readdirSync(`${from}/${family}`).filter(name => name.endsWith(".woff2"))) {
        const ttf = await decompress(readFileSync(`${from}/${family}/${font}`));
        writeFileSync(`${fonts}/${family}/${font.replace(/\.woff2$/, ".woff")}`, ttf2woff(new Uint8Array(ttf)));
    }
}
