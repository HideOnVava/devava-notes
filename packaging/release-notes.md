## Download

{{DOWNLOADS}}

64-bit Windows 10 or 11. **Java is not required**: a trimmed runtime is bundled. All files are
built automatically by GitHub Actions from this tag, so the build log shows exactly what went
into them. macOS and Linux versions are planned.

<details>
<summary><b>"Windows protected your PC"</b></summary>

The files are not code-signed (that needs a paid certificate), so SmartScreen may show this
warning the first time. Click **More info → Run anyway**.
</details>

Your notes are plain Markdown files in your Documents folder (`Devava Notes`). They are kept
when you update or uninstall, and any Markdown editor (Obsidian included) can open them.

## What's new in {{VERSION}}

{{CHANGES}}

## Verify the download (optional)

Compare the SHA-256 of the file (`certutil -hashfile <file> SHA256` in a command prompt) with:

```
{{CHECKSUMS}}
```
