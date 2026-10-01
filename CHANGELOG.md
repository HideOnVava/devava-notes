# Changelog

All notable changes to Devava Notes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). Each release on GitHub takes its notes from
the matching section of this file.

## [1.0.1] - 2026-09-30

### Fixed
- Copying or cutting text in a note (`Ctrl+C`, `Ctrl+X` or the right-click menu) emptied the
  clipboard instead of filling it, so pasting afterwards brought nothing, and text that was cut
  was lost. Both now put the text on the clipboard, ready to paste in the app or anywhere else.
- A picture on the clipboard can also be pasted with the right-click menu's *Paste*, not only
  with `Ctrl+V`.

## [1.0.0] - 2026-09-28

The first version of Devava Notes, for Windows.

### Notebooks made for what they hold
- **General**: free-form notes with tags, and pins for the ones you keep coming back to.
- **Class Notes**: one course per notebook. Lectures by date and unit, assignments with a due
  date and a status, and exams with their topics. *Upcoming* lists what is due next, late
  work shows in red, and a right-click marks an assignment as done.
- **Competitive Programming**: problems from Codeforces, AtCoder and LeetCode. Paste the
  problem's link and the judge and ID fill themselves in. Browse by algorithm (with
  subtopics such as `graphs/dijkstra`), technique, difficulty (one Easy / Medium / Hard /
  Very hard scale across the three judges), judge and status; *To review* gathers what you
  solved with help. Keep reusable code in the **Code Library** and insert it into a solution
  in two clicks; each notebook has its own C++ template.
- Every view as a list or as a table with sortable columns, with a filter box.

### Writing
- A Markdown editor (CodeMirror 6): headings and emphasis drawn as you type, code blocks
  colored by language (C++, Python, Java and more), find with `Ctrl+F`.
- A reading view (`Ctrl+E`): math with KaTeX (`$…$` and `$$…$$`), code blocks with line
  numbers and a *Copy* button, tables and task lists.
- `[[Links]]` between notes, with the titles suggested as you type; `Ctrl+click` follows one
  in the editor, a click in the reading view. A link to a note that does not exist yet
  creates it.
- `#tags` anywhere in the text, gathered with the note's *Tags* property; a click on one
  shows every note that has it.
- Pictures: paste one (`Ctrl+V`) or drop it on the note; it is saved in the notebook's
  `attachments` folder.

### Finding
- Home shows your notebooks, what is coming up in your classes and the notes you edited last.
- Quick open (`Ctrl+O`) opens any note by its title.
- Search (`Ctrl+Shift+F`) finds words in every note, whatever their accents and case.

### Everything else
- Light and dark themes.
- Notes are plain Markdown files with YAML front matter, in `Documents\Devava Notes`, so
  other Markdown editors (Obsidian included) can open them. A deleted note goes to the
  Recycle Bin.
- Every change is saved by itself a moment later, and files are written atomically.
- A Windows installer (per user, no administrator rights needed) and a portable ZIP; no Java
  to install.

[1.0.1]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.0.1
[1.0.0]: https://github.com/HideOnVava/devava-notes/releases/tag/v1.0.0
