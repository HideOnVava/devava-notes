// JavaFX's WebKit gives keyboard events their keyCode alone: their key and code are empty. The pages
// read those (Enter and Escape in main.js, and all of Excalidraw's shortcuts: Ctrl+Z, Supr, a tool's
// letter), so they are filled in from the keyCode, with the names CodeMirror's keyName gives it.
import {base, shift} from "w3c-keyname";

const CODES = {
    32: "Space", 106: "NumpadMultiply", 107: "NumpadAdd", 109: "NumpadSubtract", 110: "NumpadDecimal", 111: "NumpadDivide",
    186: "Semicolon", 187: "Equal", 188: "Comma", 189: "Minus", 190: "Period", 191: "Slash", 192: "Backquote",
    219: "BracketLeft", 220: "Backslash", 221: "BracketRight", 222: "Quote",
};
const fill = {
    key: e => (e.shiftKey ? shift : base)[e.keyCode],
    code: ({keyCode: k}) => k >= 65 && k <= 90 ? "Key" + String.fromCharCode(k) : k >= 48 && k <= 57 ? "Digit" + (k - 48)
        : k >= 96 && k <= 105 ? "Numpad" + (k - 96) : CODES[k] ?? base[k],
};
for (const name in fill) {
    const native = Object.getOwnPropertyDescriptor(KeyboardEvent.prototype, name).get;
    Object.defineProperty(KeyboardEvent.prototype, name, {
        configurable: true, enumerable: true,
        get() {
            const value = native.call(this);
            if (value || this.type === "keypress") return value;   // a keypress's keyCode is a character's, not a key's
            return fill[name](this) ?? "Unidentified";
        },
    });
}
