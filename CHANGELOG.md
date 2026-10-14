# Cambios

Aquí están los cambios importantes de cada versión de Devava Notes. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y las versiones siguen el
[versionado semántico](https://semver.org/lang/es/). Cada versión en GitHub toma su «Qué hay de
nuevo» de la sección que le toca en este archivo.

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

[2.0.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v2.0.0
[1.2.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.2.0
[1.1.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.1.0
[1.0.1]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.0.1
[1.0.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.0.0
