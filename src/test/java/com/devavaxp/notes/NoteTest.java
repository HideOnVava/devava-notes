package com.devavaxp.notes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteTest {

    @Test
    void readsThePropertiesPeopleAndObsidianWrite() {
        Note note = Note.parse("""
                ---
                judge: Codeforces
                id: "4A"
                algorithms: [math, "brute force"]
                techniques:
                  - parity
                  - greedy
                difficulty: 800 # from the site
                nested:
                  key: value
                ---
                ## Idea
                ---
                A rule inside the body is not the end of the properties.
                """);
        assertEquals("Codeforces", note.get("judge"));
        assertEquals("4A", note.get("id"));
        assertEquals(List.of("math", "brute force"), note.list("algorithms"));
        assertEquals(List.of("parity", "greedy"), note.list("techniques"));
        assertEquals("800", note.get("difficulty"));
        assertEquals("", note.get("status"));
        assertEquals("## Idea\n---\nA rule inside the body is not the end of the properties.\n", note.body);
    }

    @Test
    void settingAPropertyRewritesOnlyItsLines() {
        Note note = Note.parse("---\n# kept\njudge: Codeforces\ntechniques:\n  - parity\nnested:\n  key: value\n---\nbody\n");
        note.setList("techniques", List.of("two pointers", "a, b"));
        note.set("status", "Solved: with help");
        note.set("judge", "");
        assertEquals("---\n# kept\ntechniques: [two pointers, \"a, b\"]\nnested:\n  key: value\nstatus: \"Solved: with help\"\n---\nbody\n",
                note.text());
        Note again = Note.parse(note.text());
        assertEquals(List.of("two pointers", "a, b"), again.list("techniques"));
        assertEquals("Solved: with help", again.get("status"));
    }

    @Test
    void findsTheTagsWrittenInTheTextButNotInCode() {
        Note note = Note.parse("""
                # A heading, not a tag
                Reading #books and #to-read/next, not C# or https://x.com/#part or #1.

                ```cpp
                #include <bits/stdc++.h>
                ```
                Also `#define` in code, and #Books again.
                """);
        assertEquals(List.of("books", "to-read/next"), note.inlineTags());
    }

    @Test
    void aSnippetGivesItsFirstBlockOfCode() {
        Note snippet = Note.parse("---\nkind: snippet\n---\n## When to use\nUnion by size.\n\n"
                + "```cpp\nint find(int x) {\n    return p[x] == x ? x : p[x] = find(p[x]);\n}\n```\n\n```py\nprint(1)\n```\n");
        assertEquals(new Note.Code("cpp", "int find(int x) {\n    return p[x] == x ? x : p[x] = find(p[x]);\n}"), snippet.firstCode().orElseThrow());
        assertEquals(new Note.Code("", ""), Note.parse("```\n```").firstCode().orElseThrow());
        assertTrue(Note.parse("no code here").firstCode().isEmpty());
    }

    @Test
    void aNoteGetsPropertiesOnlyWhileItHasSome() {
        Note note = Note.parse("# Title\r\n\r\ntext");
        assertEquals("# Title\r\n\r\ntext", note.text());
        note.set("date", "2026-09-28");
        assertEquals("---\ndate: 2026-09-28\n---\n# Title\r\n\r\ntext", note.text());
        note.set("date", " ");
        assertEquals("# Title\r\n\r\ntext", note.text());
    }
}
