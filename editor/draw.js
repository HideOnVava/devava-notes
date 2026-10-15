// The drawing window of Devava Notes: Excalidraw, in Spanish, to draw the pictures of a note. Java
// opens a drawing (a .excalidraw.png, whose PNG holds its scene, so that it can be drawn on again)
// or a new one, and asks for it back as such a PNG. `npm run build` bundles it into
// src/main/resources/com/devavaxp/notes/draw.js, which is committed.
import "./draw-setup.js";
import React from "react";
import {createRoot} from "react-dom/client";
import {
    CaptureUpdateAction, Excalidraw, MIME_TYPES, MainMenu, convertToExcalidrawElements, exportToBlob, hashElementsVersion, loadFromBlob,
    serializeLibraryAsJSON
} from "@excalidraw/excalidraw";
import "@excalidraw/excalidraw/index.css";

// As on the editor's page: the page tells Java things as text, through alert().
const tell = message => window.alert(message);
window.onerror = (message, source, line) => tell(`error:${message} (line ${line})`);
window.addEventListener("unhandledrejection", e => tell(`error:${e.reason}`));

let api = null, saved = 0;
// The personal library is Java's to keep (a .excalidrawlib in the notes' folder), once it was read from there.
let library = false;
// Where the mouse was last over the drawing: a picture pasted goes there, as Excalidraw's own pastes do.
let pointer = null;
window.addEventListener("pointermove", e => {
    if (e.target instanceof HTMLCanvasElement) pointer = {x: e.clientX, y: e.clientY};
});

function Drawing() {
    const [theme, setTheme] = React.useState("light");
    window.setTheme = dark => setTheme(dark ? "dark" : "light");   // set while rendering: before Excalidraw says it is ready
    return React.createElement(Excalidraw, {
        excalidrawAPI: ready => {
            if (api) return;
            api = ready;
            window.drawingAPI = ready;   // for the tests, with a way to make elements
            window.drawingElements = convertToExcalidrawElements;
            tell("ready");
        },
        langCode: "es-ES",
        autoFocus: true,   // its shortcuts work before a first click
        theme,
        // Opening, saving and exporting are the window's: "Insertar en la nota", "Guardar".
        UIOptions: {canvasActions: {loadScene: false, saveToActiveFile: false, export: false, saveAsImage: false, toggleTheme: false}},
        onLibraryChange: items => {
            if (library) tell("library:" + serializeLibraryAsJSON(items));
        },
    }, menu());
}

/** Its menu, without the links to Excalidraw's website and social networks: the window is the app's. */
function menu() {
    const item = name => React.createElement(MainMenu.DefaultItems[name]);
    return React.createElement(MainMenu, null, item("SearchMenu"), item("ClearCanvas"), React.createElement(MainMenu.Separator),
        item("ChangeCanvasBackground"), item("Help"));
}

/** Opens a drawing: the base64 of its .excalidraw.png, or "" for a new one; and the library, its file's text or "". */
window.openDrawing = async (base64, libraryText) => {
    // The fonts it offers, with the letters of Spanish, before a text is measured in one of them: else it
    // is measured in a font of the system, and drawn in a wider one.
    await Promise.all(["Excalifont", "Nunito", "Lilita One", "Comic Shanns", "Cascadia"].map(family =>
        document.fonts.load(`20px "${family}"`, "aáeéiíoóuúüñAÁEÉIÍOÓUÚÜÑ¿?¡!0123456789").catch(() => {})));
    try {
        if (libraryText) await api.updateLibrary({libraryItems: new Blob([libraryText], {type: MIME_TYPES.excalidrawlib}), merge: false});
        library = true;
    } catch (e) {
        tell(`error:the library, left as it is: ${e.message}`);   // not overwritten by this window
    }
    if (base64) {
        const bytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0));
        const scene = await loadFromBlob(new Blob([bytes], {type: MIME_TYPES.png}), null, null);
        api.updateScene({elements: scene.elements, appState: {viewBackgroundColor: scene.appState.viewBackgroundColor},
            captureUpdate: CaptureUpdateAction.NEVER});   // not something Ctrl+Z undoes
        if (scene.files) api.addFiles(Object.values(scene.files));
        api.scrollToContent(undefined, {fitToContent: true});
    } else {
        api.resetScene();
    }
    saved = hashElementsVersion(api.getSceneElements());
    tell("opened");
};

/**
 * A picture (its base64 and type), at most 600 wide: to draw on a screenshot, say. It goes where the mouse
 * is when it is pasted, and in the middle of the view otherwise.
 */
window.addImage = async (base64, mimeType, pasted) => {
    const dataURL = `data:${mimeType};base64,${base64}`;
    const [width, height] = await new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve([img.naturalWidth, img.naturalHeight]);
        img.onerror = () => reject(new Error("not a picture"));
        img.src = dataURL;
    });
    const fileId = crypto.randomUUID(), scale = Math.min(1, 600 / Math.max(width, height));
    api.addFiles([{id: fileId, dataURL, mimeType, created: Date.now()}]);
    const {scrollX, scrollY, zoom, width: viewWidth, height: viewHeight, offsetLeft, offsetTop} = api.getAppState();
    const at = pasted && pointer || {x: offsetLeft + viewWidth / 2, y: offsetTop + viewHeight / 2};
    const w = width * scale, h = height * scale;
    const [image] = convertToExcalidrawElements([{type: "image", fileId, width: w, height: h,
        x: (at.x - offsetLeft) / zoom.value - scrollX - w / 2, y: (at.y - offsetTop) / zoom.value - scrollY - h / 2}]);
    api.updateScene({elements: [...api.getSceneElements(), image], captureUpdate: CaptureUpdateAction.IMMEDIATELY});
};

/** Whether the drawing changed since it was opened. */
window.drawingChanged = () => hashElementsVersion(api.getSceneElements()) !== saved;

/** The drawing as a PNG, twice its size and with its scene inside, as "png:<base64>" ("png:" when it is empty). */
window.exportDrawing = async () => {
    const elements = api.getSceneElements();
    if (!elements.length) {
        tell("png:");
        return;
    }
    const blob = await exportToBlob({
        elements, files: api.getFiles(), mimeType: MIME_TYPES.png, exportPadding: 20,
        appState: {...api.getAppState(), exportEmbedScene: true, exportBackground: true, exportWithDarkMode: false},
        getDimensions: (width, height) => ({width: width * 2, height: height * 2, scale: 2}),
    });
    const reader = new FileReader();
    reader.onload = () => tell("png:" + reader.result.slice(reader.result.indexOf(",") + 1));
    reader.readAsDataURL(blob);
};

createRoot(document.getElementById("root")).render(React.createElement(Drawing));
