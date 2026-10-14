package com.devavaxp.notes;

import com.devavaxp.notes.Reticula.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReticulaTest {

    @Test
    void hasTheFiftyFiveSubjectsOfIscWithWhatTheyStartWith() {
        assertEquals(55, Reticula.ISC.size());
        Set<String> keys = new HashSet<>();
        for (Subject s : Reticula.ISC) {
            assertTrue(keys.add(s.key()), s.key());
            assertTrue(s.semester() >= 1 && s.semester() <= 9, s.name());
            assertTrue(NotebookType.ALL.contains(s.type()), s.name());
            assertEquals(s.type().code(), !s.language().isEmpty(), s.name());   // a language exactly for code
        }
        Subject poo = Reticula.ISC.stream().filter(s -> s.key().equals("B12")).findFirst().orElseThrow();
        assertEquals("Programación Orientada a Objetos", poo.name());
        assertEquals(NotebookType.PROGRAMMING, poo.type());
        assertEquals("java", poo.language());
        assertTrue(poo.topics().contains("herencia"));
        assertEquals("1er semestre", Reticula.semester(1));
        assertEquals("9no semestre", Reticula.semester(9));
    }

    @Test
    void findsTheSubjectOfANotebook(@TempDir Path home) throws IOException {
        Path poo = Files.createDirectory(home.resolve("programación orientada a objetos"));   // accents and case aside
        assertEquals("B12", Reticula.of(poo).key());
        Path ed = Files.createDirectory(home.resolve("ED"));
        assertNull(Reticula.of(ed));
        Vault.setMeta(ed, "subject", "B13");
        assertEquals("Estructura de Datos", Reticula.of(ed).name());
        assertFalse(Reticula.of(ed).topics().isEmpty());
    }
}
