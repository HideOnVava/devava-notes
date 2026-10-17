# Cambios

Aquí están los cambios importantes de cada versión de Devava Notes. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y las versiones siguen el
[versionado semántico](https://semver.org/lang/es/). Cada versión en GitHub toma su «Qué hay de
nuevo» de la sección que le toca en este archivo.

## [2.4.0] - 2026-10-16

Fórmulas sin saber LaTeX, imágenes del tamaño que quieras, recuadros de colores, y las notas que
enlazan a la que lees.

### Agregado
- **Los símbolos de las fórmulas, a un clic**: **Fórmula**, en la barra de formato, abre sus
  símbolos en cinco grupos (básicos, de cálculo, letras griegas, de lógica y conjuntos, y de
  matrices y vectores), y cada uno dice qué es al pasar el ratón. Uno escribe en la fórmula
  donde está el cursor, o en una nueva, y deja el cursor en su primer hueco (en el numerador de
  una fracción, por ejemplo). Arriba está **Fórmula en su propia línea**.
- **La fórmula que escribes, dibujada debajo** mientras la escribes. Si algo está mal (una llave
  sin cerrar, un comando que no existe), dice qué, en español.
- **Imágenes mientras escribes**, debajo de su línea: arrastra su esquina, o elige **Chica**,
  **Mediana**, **Grande** u **Original** sobre ella, para cambiar su tamaño, que la vista
  previa, el PDF y la impresión respetan. Se guarda como en Obsidian (`![|400](…)`). Los
  dibujos también se ven, y un clic los abre.
- **Recuadros**: **Recuadro**, en la barra, pone una definición, un ejemplo, algo importante o
  una advertencia en un recuadro de su color, con su ícono y su título, alrededor de las líneas
  que elijas o en uno nuevo. Se escriben como en Obsidian (`> [!definition] Pila`), así que los
  suyos también se ven, igual que con sus palabras en español (`[!ejemplo]`). Se ven igual al
  escribir, en la vista previa y en papel.
- **Las notas que enlazan aquí**: al final de cada nota, al escribir y en la vista previa, las
  notas que la enlazan con `[[…]]`, de cualquier materia o cuaderno; un clic abre una.
- **Enlaces en las tablas**: `[[` en una celda también sugiere los títulos: ↑ y ↓ para elegir,
  Enter o Tab para tomar uno, Esc para cerrarlos.
- **El tamaño del texto**: `Ctrl++` y `Ctrl+-` (o `Ctrl` y la rueda del ratón) agrandan o
  achican el texto de la nota, `Ctrl+0` lo deja como era, y la app lo recuerda.
- *Ayuda* incluye el tamaño del texto y los recuadros.

## [2.3.0] - 2026-10-15

Tus notas salen en PDF y en papel tal como se ven, y llevan diagramas que se dibujan solos a
partir de su texto.

### Agregado
- **Exportar a PDF e imprimir**: el botón de la impresora, junto a *Vista previa*, guarda la
  nota como PDF, que se abre al terminar, o abre la ventana para imprimirla (`Ctrl+P`), con su
  vista previa, la impresora, las copias y las páginas. Sale como la muestra la vista previa
  (fórmulas, código con colores, tablas, imágenes, dibujos y diagramas), en hoja carta, con el
  título y lo que es la nota arriba (su materia, su tipo, su unidad y su fecha) y el número de
  página abajo. También desde el clic derecho de una nota en la lista. Lo hace Microsoft Edge,
  que viene con Windows, sin abrir otra ventana ni usar tu cuenta de Microsoft: tus notas no
  salen de tu computadora.
- **Diagramas**: **Diagrama**, en la barra de formato, agrega uno de flujo, de clases,
  entidad-relación, de secuencia o de estados, escrito como texto
  ([Mermaid](https://mermaid.js.org)) para cambiarlo como quieras; también se escribe
  ```` ```mermaid ````. Se dibuja debajo de su texto mientras lo escribes y, si algo está mal,
  dice en qué línea. La vista previa, el PDF y la impresión lo muestran dibujado, con los
  colores de la app (y en tema oscuro, con los suyos).
- *Ayuda* incluye `Ctrl+P` y los diagramas.

## [2.2.0] - 2026-10-15

Tus enlaces siguen a una nota cuando le cambias el nombre, la app se abre una sola vez y avisa
cuando hay una versión nueva.

### Agregado
- **Aviso de versión nueva**: al abrir, la app pregunta a GitHub cuál es la última versión, y si
  hay una más nueva la barra muestra **Nueva versión** con su número; un clic abre su página para
  descargarla e instalarla sobre esta. Sin internet no pasa nada, y solo se envía el número de tu
  versión, nada de tus notas.
- **Reportar un problema**, en *Ayuda* y en *Opciones*: abre GitHub con una plantilla, la versión
  de la app y las últimas líneas de su registro de errores, sin tu nombre de usuario de Windows,
  para que lo revises antes de publicarlo. El registro completo queda en
  `%LOCALAPPDATA%\devava XP Studios\Devava Notes`, fuera de tus notas.
- *Ayuda* dice qué versión tienes.

### Cambiado
- **Cambiar el nombre de una nota actualiza sus `[[enlaces]]`** en todas tus materias y
  cuadernos, con el texto que muestran (`[[Pilas|la pila]]`). Si en algún lugar el nombre nuevo
  llevaría a otra nota que se llama igual, o no cabe entre corchetes, ese enlace se queda como
  estaba y la app te dice dónde.
- **La app se abre una sola vez**: abrirla de nuevo trae al frente la ventana que ya está, para
  que dos ventanas no guarden una encima de lo que escribe la otra.
- La app se conecta a internet por el proxy que tenga Windows, como los navegadores.

## [2.1.0] - 2026-10-15

Tablas que se escriben en sus celdas, dibujos dentro de la app, y los cuadernos libres aparte de
las materias.

### Agregado
- **Tablas** que se ven y se escriben como cuadrícula, igual que una hoja de cálculo: **Tab**
  pasa a la celda siguiente, **Enter** a la de abajo (al final agrega una fila, y en una fila
  nueva vacía sale de la tabla), y las flechas van de celda en celda y entran o salen de la
  tabla. Arriba de cada una, **+ Fila**, **+ Columna**, un menú **⋯** para quitar una fila o
  una columna o alinearla, **Copiar** (se pega con sus celdas en Word, Excel o Google Docs) y
  **Quitar**. La nota guarda una tabla de Markdown normal, que otros programas leen.
- Tres maneras de **crear una tabla**: **Tabla** en la barra de formato, con cuadros como en
  Word para elegir columnas y filas; pegar celdas de Excel, Google Sheets, SQL Developer o una
  página (`Ctrl+Z` deja lo pegado como texto); o escribir el encabezado, como
  `| Paso | i | suma |`, y dar Enter. Las celdas pegadas dentro de una tabla la llenan desde
  la celda donde estás.
- **Dibujos** con Excalidraw, en español y en una ventana de la app: **Dibujo** en la barra
  de formato, figuras, flechas y texto a mano, imágenes (**Imagen** o `Ctrl+V` de una
  captura) para dibujar sobre ellas, **Mermaid a Excalidraw** para convertir el texto de un
  diagrama, y una **biblioteca** de figuras que comparten todos tus dibujos. **Insertar en la
  nota** lo guarda en `attachments`; un clic en el dibujo (o *Editar dibujo* al escribir) lo
  vuelve a abrir. En el tema oscuro, el dibujo también se oscurece.
- **Cuadernos libres** en Inicio, aparte de las materias, y **Nuevo cuaderno libre** para
  crear uno (Libre o Programación competitiva).

### Cambiado
- Inicio agrupa y cuenta por periodo **solo las materias**: un periodo ya no cuenta como
  materias tus cuadernos libres. *Nueva materia* y la primera vez ofrecen solo materias;
  Residencia Profesional, Servicio Social y Actividades Complementarias son cuadernos libres.
- Los `#` de un título cuelgan en el margen, así que su texto empieza donde el de sus
  párrafos; las listas se sangran por nivel, y lo que sigue de un elemento empieza donde su
  texto.
- Un cuaderno libre ya no tiene *Cambiar periodo…*, y *Opciones* dice *Importar un cuaderno
  (ZIP)…*.

### Corregido
- Un clic en una nota pone el cursor donde se hizo, también debajo de bloques de código,
  títulos e imágenes, y ↑ y ↓ pasan por cada línea sin saltarse ninguna.
- Enter y Esc en el buscador de lenguajes de un bloque de código.
- Vista previa respeta la alineación de las columnas de una tabla (`:---:` no centraba), y una
  tabla muy ancha se desplaza de lado en vez de salirse de la página.

## [2.0.0] - 2026-10-14

La versión hecha para los estudiantes de Ingeniería en Sistemas Computacionales del Instituto
Tecnológico de La Laguna: solo en español, con las materias de la retícula y con sesiones de
código.

### Agregado
- **Nueva materia, desde la retícula de ISC**: escribe parte del nombre y la app encuentra la
  materia, con su semestre, y propone su tipo y el lenguaje de su código (*Estructura de Datos*:
  Programación, en Java). Las 55 materias vienen de Devava Kardex; también se puede usar
  cualquier otro nombre.
- **La primera vez**, Inicio pregunta qué materias llevas este semestre, marcadas en la
  retícula, y agrega *Primeros pasos* con una nota sobre cómo usar la app.
- **Programación**, un tipo nuevo para las materias con código: apuntes, **sesiones de código**
  (lo que se programó en clase y lo que imprimió), *mis fragmentos*, tareas y prácticas, y
  exámenes. Bases de datos también tiene sesiones de código.
- **Los bloques de código son tarjetas** al escribir: arriba de cada uno, su lenguaje (un menú
  con Java, C++, C, Python, C#, JavaScript, SQL, PL/SQL y cualquier otro), *Copiar* y *Quitar*;
  el código, con colores y números de línea. Un bloque de **Salida** guarda lo que imprimió el
  programa, y **+ Bloque de código** agrega otro en el lenguaje de la materia, que se ve junto
  a su nombre.
- **Una barra de formato** sobre la nota: negrita, cursiva, título, lista, casilla, enlace,
  imagen, fórmula y bloque de código, con un clic; también `Ctrl+B` y `Ctrl+I`.
- **Inicio por periodo escolar** (*Ene–Jun*, *Verano*, *Ago–Dic*): las materias de este
  semestre primero y las anteriores plegadas; *Cambiar periodo…* mueve una materia a otro.
- **Temas sugeridos por materia**, en el menú ▾ de *Temas* y en el *Tema* de los apuntes.
- **Ayuda**: los atajos de teclado y dónde quedan tus notas.
- Una vista vacía dice qué va en ella, con el botón que crea su primera nota.

### Cambiado
- **Solo en español**, sea cual sea el idioma de Windows; el instalador también.
- **Un diseño nuevo**: otros colores, íconos y botones que dicen lo que hacen (*Nueva materia*,
  *Editar* y *Vista previa*, *Opciones*).
- Los tipos se llaman **Materia** (antes *Apuntes de clase*) y **Libre** (antes *General*); las
  clases ahora son *apuntes*, y la vista de tareas, *Tareas y prácticas*. Los cuadernos y sus
  archivos no cambian.
- En Bases de datos, *+ Consulta* dio paso a *Bloque de código*, en PL/SQL, y su biblioteca de
  código se llama *Mis fragmentos*.

## [1.2.0] - 2026-09-30

### Agregado
- **Devava Notes en español**: cada pantalla, menú, diálogo y mensaje, las fechas y el
  calendario, el panel de búsqueda del editor (`Ctrl+F`) y los títulos de las notas nuevas. La
  app hablaba el idioma de Windows, y *⋯ → Idioma* cambiaba entre English y Español al momento.

### Cambiado
- Lo que guardan las notas sigue en inglés (`status: Solved`, `level: Medium`) en cualquier
  idioma, para que un cuaderno compartido funcione igual para todos; solo cambia cómo se ve.
- La vista *Fijadas* de los cuadernos generales se llama *Notas fijadas*.

## [1.1.0] - 2026-09-30

### Agregado
- **Cuadernos de Bases de datos**, para un curso de bases de datos con Oracle: clases por fecha,
  unidad y tema; ejercicios de SQL y PL/SQL con su nivel y su estado (*Por repasar* junta los
  que se resolvieron con ayuda); las tablas del esquema con que trabajas; una biblioteca de
  consultas; y tareas y exámenes, que aparecen en *Lo que se acerca* y en Inicio como en los
  apuntes de clase. Los temas propuestos van de `consultas/joins` a `plsql/disparadores`, y
  *Por tema* pone `plsql/cursores` dentro de `plsql`.
- El código en bloques ` ```plsql ` sale con los colores de las palabras de Oracle (`VARCHAR2`,
  `SYSDATE`, los comandos de SQL*Plus); *+ Consulta* agrega uno de esos bloques en el cursor.
- **Compartir un cuaderno**: *Exportar como ZIP…* en su menú, e *Importar cuaderno…* en el menú
  de Inicio para agregar uno que alguien compartió, con su tipo y sus imágenes.

### Cambiado
- Las listas de Inicio muestran el título de cada nota sobre el nombre de su cuaderno, así que
  los nombres largos de las materias ya no cortan los títulos.
- Un bloque de código de la vista de lectura lleva el nombre de su lenguaje (*C++*, *PLSQL*).
- *Insertar fragmento…* funciona en cualquier cuaderno con biblioteca de código.

### Corregido
- Una nota que no se podía leer por un momento (porque OneDrive la estaba sincronizando) ya no
  esconde el resto de su materia de *Lo que se acerca* en Inicio.

## [1.0.1] - 2026-09-30

### Corregido
- Copiar o cortar texto en una nota (`Ctrl+C`, `Ctrl+X` o el menú del clic derecho) vaciaba el
  portapapeles en lugar de llenarlo: pegar después no traía nada, y el texto cortado se perdía.
  Ahora los dos ponen el texto en el portapapeles, listo para pegarlo en la app o en cualquier
  otro lado.
- Una imagen en el portapapeles también se puede pegar con *Pegar* del menú del clic derecho, no
  solo con `Ctrl+V`.

## [1.0.0] - 2026-09-28

La primera versión de Devava Notes, para Windows.

### Cuadernos hechos para lo que llevan
- **General**: notas libres con etiquetas, y fijadas las que siempre vuelves a ver.
- **Apuntes de clase**: una materia por cuaderno. Clases por fecha y unidad, tareas con fecha de
  entrega y estado, y exámenes con sus temas. *Lo que se acerca* dice lo que toca después, lo
  atrasado sale en rojo, y un clic derecho marca una tarea como hecha.
- **Programación competitiva**: problemas de Codeforces, AtCoder y LeetCode. Pega el enlace del
  problema y su juez y su ID se llenan solos. Se ven por algoritmo (con subtemas como
  `graphs/dijkstra`), técnica, dificultad (una sola escala, de fácil a muy difícil, para los
  tres jueces), juez y estado; *Por repasar* junta lo que resolviste con ayuda. El código que
  reutilizas va en la **biblioteca de código** y entra en una solución en dos clics; cada
  cuaderno tiene su plantilla de C++.
- Cada vista como lista o como tabla con columnas para ordenar, con un filtro.

### Escribir
- Un editor de Markdown (CodeMirror 6): títulos y énfasis dibujados al escribir, bloques de
  código con colores según su lenguaje (C++, Python, Java y más), y buscar con `Ctrl+F`.
- Una vista de lectura (`Ctrl+E`): fórmulas con KaTeX (`$…$` y `$$…$$`), bloques de código con
  números de línea y un botón para copiarlos, tablas y listas de pendientes.
- `[[Enlaces]]` entre notas, con los títulos sugeridos al escribir; `Ctrl+clic` sigue uno en el
  editor, un clic en la vista de lectura. Un enlace a una nota que aún no existe la crea.
- `#Etiquetas` en cualquier parte del texto, juntas con las *Etiquetas* de la nota; un clic en
  una muestra las notas que la tienen.
- Imágenes: pega una (`Ctrl+V`) o arrástrala a la nota; se guarda en la carpeta `attachments`
  del cuaderno.

### Encontrar
- Inicio muestra tus cuadernos, lo que se acerca en tus clases y las últimas notas que editaste.
- Abrir rápido (`Ctrl+O`) abre cualquier nota por su título.
- Buscar (`Ctrl+Shift+F`) encuentra palabras en todas las notas, sin importar acentos ni
  mayúsculas.

### Todo lo demás
- Temas claro y oscuro.
- Las notas son archivos Markdown con encabezado YAML, en `Documentos\Devava Notes`, que otros
  editores de Markdown (Obsidian incluido) pueden abrir. Una nota borrada va a la Papelera de
  reciclaje.
- Cada cambio se guarda solo, un momento después, y los archivos se escriben completos o no se
  escriben.
- Un instalador para Windows (por usuario, sin permisos de administrador) y un ZIP portable; no
  hay que instalar Java.

[2.4.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v2.4.0
[2.3.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v2.3.0
[2.2.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v2.2.0
[2.1.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v2.1.0
[2.0.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v2.0.0
[1.2.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.2.0
[1.1.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.1.0
[1.0.1]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.0.1
[1.0.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.0.0
