package com.devavaxp.notes;

import com.devavaxp.notes.NotebookType.Badge;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgendaTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    void anAssignmentIsAheadUntilItIsDoneAndSaysHowNearItIs() {
        Note late = note("kind: assignment\ndue: 2026-09-25\nstatus: Pending");
        assertTrue(Agenda.upcoming(late, TODAY));
        assertTrue(Agenda.late(late, TODAY));
        assertEquals(new Badge("3 days late", "#DC2626"), Agenda.badge(late, TODAY));
        assertEquals("Due today", Agenda.badge(note("kind: assignment\ndue: 2026-09-28"), TODAY).text());
        assertEquals("Due tomorrow", Agenda.badge(note("kind: assignment\ndue: 2026-09-29"), TODAY).text());
        assertEquals("Due in 3 days", Agenda.badge(note("kind: assignment\ndue: 2026-10-01"), TODAY).text());
        assertEquals("Due Oct 20", Agenda.badge(note("kind: assignment\ndue: 2026-10-20"), TODAY).text());

        Note done = note("kind: assignment\ndue: 2026-09-25\nstatus: Done");
        assertFalse(Agenda.upcoming(done, TODAY));
        assertFalse(Agenda.late(done, TODAY));
        assertEquals("Done", Agenda.badge(done, TODAY).text());
        assertFalse(Agenda.upcoming(note("kind: assignment\ndue: someday"), TODAY));   // no day to put it on
    }

    @Test
    void anExamIsAheadUntilItsDayAndLecturesAreNeverDue() {
        assertTrue(Agenda.upcoming(note("kind: exam\ndate: 2026-09-28"), TODAY));
        assertEquals("In 5 days", Agenda.badge(note("kind: exam\ndate: 2026-10-03"), TODAY).text());
        Note past = note("kind: exam\ndate: 2026-09-01");
        assertFalse(Agenda.upcoming(past, TODAY));
        assertEquals("Past", Agenda.badge(past, TODAY).text());
        assertEquals("Grade 95", Agenda.badge(note("kind: exam\ndate: 2026-09-01\ngrade: 95"), TODAY).text());

        Note lecture = note("date: 2026-09-28\nunit: Limits");
        assertEquals(TODAY, Agenda.when(lecture));
        assertFalse(Agenda.upcoming(lecture, TODAY));
        assertNull(Agenda.badge(lecture, TODAY));
    }

    private static Note note(String properties) {
        return Note.parse("---\n" + properties + "\n---\n");
    }
}
