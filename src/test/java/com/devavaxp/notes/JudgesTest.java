package com.devavaxp.notes;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgesTest {

    @Test
    void aLinkSaysTheJudgeTheProblemAndATitle() {
        assertEquals(problem("Codeforces", "4A", "CF 4A"), Judges.fromLink("https://codeforces.com/problemset/problem/4/A"));
        assertEquals(problem("Codeforces", "1850G", "CF 1850G"), Judges.fromLink(" https://codeforces.com/contest/1850/problem/g?locale=es "));
        assertEquals(problem("Codeforces", "104114B1", "CF 104114B1"), Judges.fromLink("https://mirror.codeforces.com/gym/104114/problem/B1"));
        assertEquals(problem("AtCoder", "abc300_a", "ABC300 A"), Judges.fromLink("https://atcoder.jp/contests/abc300/tasks/abc300_a"));
        assertEquals(problem("LeetCode", "two-sum", "Two Sum"), Judges.fromLink("https://leetcode.com/problems/two-sum/description/"));
        assertEquals(Optional.empty(), Judges.fromLink("https://cses.fi/problemset/task/1633"));
    }

    @Test
    void everyJudgeLandsOnOneScaleUnlessTheNoteSaysOtherwise() {
        assertEquals("Easy", level("judge: Codeforces\ndifficulty: 800"));
        assertEquals("Medium", level("judge: Codeforces\ndifficulty: 1200"));
        assertEquals("Hard", level("judge: Codeforces\ndifficulty: 1600"));
        assertEquals("Very hard", level("judge: Codeforces\ndifficulty: 2100"));
        assertEquals("Medium", level("judge: LeetCode\ndifficulty: medium"));
        assertEquals("Medium", level("judge: AtCoder\ndifficulty: 1200"));
        assertEquals("Easy", level("judge: AtCoder\nid: abc300_b"));
        assertEquals("Hard", level("judge: AtCoder\nid: abc300_e"));
        assertEquals("Hard", level("judge: AtCoder\nid: arc150_c"));
        assertEquals("", level("judge: Other"));
        assertEquals("Easy", level("judge: Codeforces\ndifficulty: 2400\nlevel: Easy"));   // the note's own word wins
        assertEquals("#03A89E", Judges.color(Note.parse("---\njudge: Codeforces\ndifficulty: 1500\n---\n")));   // specialist cyan
    }

    private static Optional<Judges.Problem> problem(String judge, String id, String title) {
        return Optional.of(new Judges.Problem(judge, id, title));
    }

    private static String level(String properties) {
        return Judges.level(Note.parse("---\n" + properties + "\n---\n"));
    }
}
