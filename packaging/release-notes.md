## Descarga

{{DOWNLOADS}}

Windows 10 u 11 de 64 bits. **No necesitas Java**: la app trae el suyo. GitHub Actions compila
todos los archivos desde esta etiqueta, así que el registro de la compilación muestra
exactamente qué lleva cada uno. Las versiones para macOS y Linux vienen después.

<details>
<summary><b>«Windows protegió su PC»</b></summary>

Los archivos no están firmados (para eso hace falta un certificado de pago), así que SmartScreen
puede mostrar esta advertencia la primera vez. Haz clic en **Más información → Ejecutar de todas
formas**.
</details>

Tus notas son archivos Markdown en tu carpeta Documentos (`Devava Notes`). Se quedan cuando
actualizas o desinstalas la app, y cualquier editor de Markdown (Obsidian incluido) las abre.

## Qué hay de nuevo en la {{VERSION}}

{{CHANGES}}

## Verificar la descarga (opcional)

Compara el SHA-256 del archivo (`certutil -hashfile <archivo> SHA256` en una ventana de
comandos) con:

```
{{CHECKSUMS}}
```
