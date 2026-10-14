# Devava Notes

[![Descargar la última versión](https://img.shields.io/github/v/release/HideOnVava/devava-notes?label=descargar&color=2B4FD8)](https://github.com/HideOnVava/devava-notes/releases/latest)
[![CI](https://github.com/HideOnVava/devava-notes/actions/workflows/ci.yml/badge.svg)](https://github.com/HideOnVava/devava-notes/actions/workflows/ci.yml)
[![Plataforma: Windows](https://img.shields.io/badge/plataforma-Windows-0078D4)](#descarga)
[![Licencia: MIT](https://img.shields.io/badge/licencia-MIT-blue.svg)](LICENSE)

La app de apuntes de los estudiantes de **Ingeniería en Sistemas Computacionales** del
**Instituto Tecnológico de La Laguna**, hecha por **devava XP Studios**.

Cada materia del semestre tiene su cuaderno, que sabe lo que lleva: tus **apuntes** por unidad,
las **tareas y prácticas** con su fecha de entrega, los **exámenes**, y en las materias de
programación, **sesiones de código** con el código y lo que imprimió. Las materias salen de la
retícula de ISC: escribes «estruc» y la app propone *Estructura de Datos*, de Programación y en
Java. Toda la app está en español, y tus notas son archivos de texto en tu computadora: sin
cuentas, sin nube y sin instalar Java.

| Inicio | Una sesión de código |
| --- | --- |
| ![Inicio: las materias del semestre, lo que se acerca y lo último que escribiste](docs/screenshots/inicio.png) | ![Una sesión de Estructura de Datos con su código en Java](docs/screenshots/sesion-de-codigo.png) |

| Nueva materia, desde la retícula | La primera vez |
| --- | --- |
| ![Las materias de bases de datos de la retícula, con el tipo y el lenguaje que propone la app](docs/screenshots/nueva-materia.png) | ![La retícula de ISC por semestre, para elegir las materias del semestre](docs/screenshots/primer-uso.png) |

| Una materia, en vista previa | Bases de datos, en tema oscuro |
| --- | --- |
| ![Apuntes de Cálculo Vectorial con sus fórmulas](docs/screenshots/materia.png) | ![Un ejercicio de Taller de Bases de Datos con su consulta en PL/SQL](docs/screenshots/bases-de-datos-oscuro.png) |

## Descarga

**[⬇ Descarga la última versión](https://github.com/HideOnVava/devava-notes/releases/latest)**
para Windows 10 u 11 de 64 bits. En esa página, en **Assets**:

| Archivo | Luego |
| --- | --- |
| `…-windows-x64-setup.exe` | **Recomendado.** Ábrelo. Si Windows muestra *«Windows protegió su PC»*, haz clic en **Más información → Ejecutar de todas formas** ([por qué](#por-qué-windows-muestra-una-advertencia)). Deja la carpeta que propone y termina: Devava Notes queda en el menú Inicio y, si no quitas la casilla, en el escritorio. No necesita permisos de administrador. |
| `…-windows-x64-portable.zip` | Sin instalar: descomprímelo donde quieras (sirve una USB) y abre `Devava Notes.exe`. |

Las versiones para macOS y Linux vienen después.

### Primeros pasos

1. **Elige tus materias.** La primera vez, la app te muestra la retícula de ISC por semestre:
   marca las que llevas y cada una tendrá su cuaderno, con su tipo y su lenguaje. Después
   agrega más con **Nueva materia**.
2. **Escribe.** **Nueva nota** (`Ctrl+N`) ofrece lo que lleva cada materia: un apunte, una
   sesión de código, una tarea o un examen. La barra sobre la nota pone negritas, listas,
   fórmulas, imágenes y bloques de código con un clic.
3. **Llena sus datos** arriba del texto (la entrega de una tarea, los temas de un examen):
   Inicio te dice lo que se acerca, y lo atrasado sale en rojo.

La nota **Primeros pasos → Cómo usar Devava Notes** tiene un ejemplo de todo; bórrala cuando ya
no la necesites.

### Actualizar y desinstalar

Una versión nueva se instala igual: el instalador reemplaza la anterior. Para desinstalarla:
*Configuración → Aplicaciones → Aplicaciones instaladas → Devava Notes → Desinstalar* (la
portable: borra su carpeta). En los dos casos tus notas se quedan en tu carpeta Documentos.

### ¿Por qué Windows muestra una advertencia?

Las descargas no están firmadas: un certificado de firma se paga cada año, y este es un
proyecto gratuito. Por eso Windows muestra *«Windows protegió su PC»* la primera vez; **Más
información → Ejecutar de todas formas** la abre, y Windows lo recuerda. GitHub Actions
compila cada versión desde el código de su etiqueta, así que el registro de la compilación
muestra exactamente qué lleva cada archivo, y cada versión publica sus sumas SHA-256.

## Tipos de materia

Cada tipo trae sus datos (arriba de la nota), sus plantillas para las notas nuevas y sus vistas
(a la izquierda). Cada vista muestra sus notas como lista o como tabla, con columnas para
ordenar.

- **Materia** (Cálculo, Física, Química, Contabilidad…): *apuntes* (fecha, unidad, tema),
  *tareas y prácticas* (entrega, estado) y *exámenes* (fecha, temas, calificación). Vistas:
  apuntes, por unidad, **lo que se acerca** (lo pendiente, lo más próximo primero y lo
  atrasado en rojo), tareas y prácticas, exámenes y todas las notas. Clic derecho en una tarea
  para marcarla como hecha.
- **Programación** (Fundamentos de Programación, POO, Estructura de Datos, Tópicos, Web…): lo de
  una Materia, más **sesiones de código** (lo que se programó en clase y lo que imprimió) y
  **mis fragmentos**, el código para reutilizar. Cada materia tiene su lenguaje (Java, C++, C#,
  Python…), que se ve junto a su nombre: los bloques de código nuevos empiezan en él.
- **Bases de datos** (Fundamentos, Taller y Administración de Bases de Datos): lo de
  Programación, con SQL o con el PL/SQL de Oracle, más *ejercicios* (temas, nivel, estado),
  las *tablas* del esquema y la vista **por tema** (`plsql/cursores` queda dentro de `plsql`;
  los temas propuestos van de `consultas/joins` a `plsql/disparadores`).
- **Programación competitiva**: problemas de Codeforces, AtCoder y LeetCode. Pega el enlace del
  problema y se llenan su juez, su ID y un bloque de solución con tu plantilla de C++. Vistas
  por algoritmo (`graphs/dijkstra` dentro de `graphs`), técnica, dificultad (una escala para
  los tres jueces, en sus colores), juez y estado; **por repasar** junta lo que resolviste con
  ayuda, y la **biblioteca de código** guarda lo que reutilizas.
- **Libre**: lo que no es una materia (ideas, la residencia, proyectos), con etiquetas y notas
  fijadas.

En Inicio, las materias se agrupan por periodo escolar (*Ene–Jun*, *Verano*, *Ago–Dic*) y los
semestres anteriores quedan plegados; *Cambiar periodo…* mueve una materia a otro. **Lo que se
acerca** junta las entregas y los exámenes de todas tus materias.

## Escribir

Las notas son Markdown, pero no hace falta saberlo: la barra sobre la nota lo escribe por ti, y
**Vista previa** (`Ctrl+E`) muestra la nota terminada.

- **Bloques de código** como tarjetas: arriba de cada uno eliges su lenguaje (Java, C++, C,
  Python, C#, JavaScript, SQL, PL/SQL o cualquier otro), lo copias o lo quitas; el código sale
  con colores y con números de línea. Un bloque de **Salida** guarda lo que imprimió el
  programa, y **+ Bloque de código** agrega otro en el lenguaje de la materia. Quien sabe
  Markdown puede escribir ```` ```java ```` y Enter.
- **Fórmulas**: `$e^{i\pi} + 1 = 0$` en el texto, o en su propia línea entre `$$`, dibujadas
  por KaTeX.
- **Enlaces** entre notas: escribe `[[` y elige su título. `Ctrl+clic` sigue un enlace al
  escribir, un clic en la vista previa; un enlace a una nota que aún no existe la crea.
- **Etiquetas**: `#tema` en cualquier parte del texto (también `#tema/subtema`); un clic en una
  muestra las notas que la tienen.
- **Imágenes**: pega una con `Ctrl+V` (una captura de pantalla, por ejemplo) o arrastra el
  archivo a la nota; se guarda en la carpeta `attachments` de la materia.
- **Temas sugeridos**: en las materias de la retícula, el menú ▾ de *Temas* propone los de la
  materia (herencia y polimorfismo en POO, pilas y colas en Estructura de Datos…).
- Tablas, listas de pendientes (`- [ ]`) y tachado, como en GitHub.

## Atajos de teclado

| Tecla | Qué hace |
| --- | --- |
| `Ctrl+N` | Nueva materia (en Inicio) o nueva nota (en una materia) |
| `Ctrl+O` | Abrir cualquier nota por su título |
| `Ctrl+Shift+F` | Buscar en el texto de todas las notas (sin importar acentos ni mayúsculas) |
| `Ctrl+E` | Pasar entre Editar y Vista previa |
| `Ctrl+F` | Buscar y reemplazar en la nota |
| `Ctrl+B`, `Ctrl+I` | Negrita, cursiva |
| `Ctrl+V` | Pegar texto, o una imagen |
| `[[` | Enlazar otra nota, con sus títulos sugeridos |
| `Ctrl+clic` | Seguir el `[[enlace]]` bajo el ratón, al escribir |
| `Ctrl+Z` / `Ctrl+Y` | Deshacer / rehacer |

## Dónde quedan tus notas

Todo queda en una carpeta `Devava Notes` dentro de tu carpeta Documentos (también si OneDrive
la sincroniza). Cada materia es una carpeta, y cada nota un archivo `.md` con su título:

```
Documentos\Devava Notes\
├── settings.json                 el tema
└── Estructura de Datos\
    ├── .notebook.json            su tipo, su lenguaje, su periodo y su materia en la retícula
    ├── attachments\              las imágenes de sus notas
    └── Sesión 5 · Pilas.md
```

Los datos de una nota son su encabezado YAML, así que los archivos se abren en cualquier editor
de Markdown (Obsidian incluido), y las notas escritas en otro lado aparecen en la app. Sus
valores se guardan en inglés (`status: Solved`), como en las versiones anteriores, aunque la
app los muestre en español:

~~~markdown
---
kind: session
date: 2026-10-12
unit: 3. Estructuras lineales
topics: [pilas]
---
## Código

```java
Deque<Character> pila = new ArrayDeque<>();
```
~~~

- Cada cambio se guarda solo, un momento después, y cada archivo se escribe completo o no se
  escribe: un apagón nunca deja media nota.
- Borrar una nota o una materia la manda a la Papelera de reciclaje.
- Las carpetas que crees a mano son materias de tipo Libre.
- **Para compartir una materia** (tus apuntes con tus compañeros, por ejemplo): en su menú,
  *Exportar como ZIP…*. Quien recibe el ZIP la agrega con *Opciones → Importar una materia
  (ZIP)…* en Inicio, y se abre tal como estaba, con su tipo y sus imágenes.
- Para guardar las notas en otro lugar, abre la app con `-Dnotes.home=<carpeta>` (desde el
  código) o define la variable de entorno `JAVA_TOOL_OPTIONS=-Dnotes.home=<carpeta>` (la app
  instalada).

---

## Para desarrolladores

Java 21, JavaFX 21 (con WebView), Maven (con su wrapper), Gson y commonmark-java. El editor es
CodeMirror 6, con KaTeX para las fórmulas, empaquetado por esbuild en un solo script que carga
el WebView. Las pruebas usan JUnit 5; el compilador corre con `-Xlint:all` y la compilación
debe quedar sin advertencias. El código y sus comentarios están en inglés; lo que ve el
estudiante, en español.

```bash
./mvnw javafx:run                  # correr desde el código (requiere el JDK 21)
./mvnw test                        # las pruebas unitarias
./mvnw verify -Dsmoke=true         # también abre la ventana real y guarda capturas en target/smoke
```

El código del editor está en [`editor/main.js`](editor/main.js). Después de cambiarlo, vuelve a
empaquetarlo (con Node.js 20 o más nuevo) y sube el resultado,
`src/main/resources/com/devavaxp/notes/editor.js`:

```bash
cd editor
npm install
npm run build
```

La retícula está en
[`reticula-isc.json`](src/main/resources/com/devavaxp/notes/reticula-isc.json): las 55
materias de ISC de Devava Kardex, con el tipo, el lenguaje y los temas que la app propone para
cada una.

El ícono lo dibuja [`tools/icon/Icon.java`](tools/icon/Icon.java): `java tools/icon/Icon.java`
escribe `packaging/icon.png`, `packaging/icon.ico` y el ícono de la ventana.

### Compilar la aplicación

`packaging/build-windows.ps1` compila con el wrapper de Maven, copia las dependencias, arma un
runtime de Java recortado con `jlink` (los módulos de `packaging/jdk-modules.txt` más JavaFX) y
llama a `jpackage` con ese runtime y los jars en el class path. Necesita un JDK 21 completo (con
su carpeta `jmods`), que encuentra por `JAVA_HOME` o `-JdkHome`:

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\build-windows.ps1
```

El resultado es `dist\Devava Notes\Devava Notes.exe`. `-Destination "$env:LOCALAPPDATA\Programs" -Shortcut`
la instala para ti con un acceso directo en el escritorio, y `-Type exe` arma el instalador, en
español, que necesita el [WiX Toolset 3.x](https://github.com/wixtoolset/wix3/releases).

### Publicar una versión

GitHub Actions compila, prueba y publica cada versión
([`.github/workflows/release.yml`](.github/workflows/release.yml)), así que cada descarga sale
de una compilación limpia de un commit etiquetado. Para publicar, por ejemplo, la versión 2.1.0:

1. Pon `<version>2.1.0</version>` en `pom.xml`.
2. Agrega una sección `## [2.1.0] - <fecha>` a [`CHANGELOG.md`](CHANGELOG.md) (es el «Qué hay
   de nuevo» de la versión) y su enlace al final del archivo.
3. Sube los cambios, y luego la etiqueta:

   ```bash
   git tag v2.1.0
   git push origin v2.1.0
   ```

Correr el workflow a mano desde la pestaña *Actions* es un ensayo: compila y prueba todo y
guarda los archivos como artefactos, sin publicar nada.

## Licencia

[MIT](LICENSE) © 2026 devava XP Studios. La app incluye CodeMirror y KaTeX (MIT),
commonmark-java (BSD-2-Clause), Gson (Apache-2.0) y un runtime de Java con JavaFX (GPL-2.0 con
la Classpath Exception); ver [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
