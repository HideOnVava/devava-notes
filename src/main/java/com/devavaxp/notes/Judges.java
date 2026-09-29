package com.devavaxp.notes;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What Devava Notes knows about online judges without going online: what a problem's link says,
 * and how hard a problem is on one scale for all of them, in the colors each judge uses.
 */
final class Judges {

    /** A problem as its link describes it; the title is a suggestion ("CF 4A", "ABC300 A", "Two Sum"). */
    record Problem(String judge, String id, String title) {
    }

    static final List<String> LEVELS = List.of("Easy", "Medium", "Hard", "Very hard");

    /** Codeforces' own tags, suggested for a problem's algorithms. */
    static final List<String> ALGORITHMS = List.of("2-sat", "binary search", "bitmasks", "brute force",
            "chinese remainder theorem", "combinatorics", "constructive algorithms", "data structures", "dfs and similar",
            "divide and conquer", "dp", "dsu", "expression parsing", "fft", "flows", "games", "geometry", "graph matchings",
            "graphs", "greedy", "hashing", "implementation", "interactive", "math", "matrices", "meet-in-the-middle",
            "number theory", "probabilities", "schedules", "shortest paths", "sortings", "string suffix structures",
            "strings", "ternary search", "trees", "two pointers");

    /** Common techniques, suggested for a problem's techniques. */
    static final List<String> TECHNIQUES = List.of("binary search on answer", "bitmask dp", "contribution technique",
            "coordinate compression", "difference array", "digit dp", "dp on trees", "exchange argument", "knapsack",
            "meet in the middle", "monotonic queue", "monotonic stack", "offline queries", "precomputation", "prefix sums",
            "sliding window", "small to large", "sqrt decomposition", "sweep line", "two pointers");

    /** A C++ start for new solutions, until the notebook has its own. */
    static final String CPP = """
            #include <bits/stdc++.h>
            using namespace std;

            int main() {
                ios::sync_with_stdio(false);
                cin.tie(nullptr);

                return 0;
            }""";

    private static final Pattern CODEFORCES = Pattern.compile(
            "(?i)^https?://(?:[\\w-]+\\.)?codeforces\\.com/(?:(?:contest|gym)/(\\d+)/problem|problemset/problem/(\\d+))/([a-z]\\d*)");
    private static final Pattern ATCODER = Pattern.compile("(?i)^https?://atcoder\\.jp/contests/([\\w-]+)/tasks/([\\w-]+)");
    private static final Pattern LEETCODE = Pattern.compile("(?i)^https?://(?:www\\.)?leetcode\\.(?:com|cn)/problems/([\\w-]+)");
    private static final Pattern ATCODER_TASK = Pattern.compile("(?i)^(abc|arc|agc)\\d+_([a-z])");

    private Judges() {
    }

    static Optional<Problem> fromLink(String link) {
        String url = link.strip();
        Matcher m = CODEFORCES.matcher(url);
        if (m.find()) {
            String id = (m.group(1) != null ? m.group(1) : m.group(2)) + m.group(3).toUpperCase(Locale.ROOT);
            return Optional.of(new Problem("Codeforces", id, "CF " + id));
        }
        m = ATCODER.matcher(url);
        if (m.find()) {
            String task = m.group(2).toLowerCase(Locale.ROOT), contest = m.group(1).toUpperCase(Locale.ROOT);
            int underscore = task.lastIndexOf('_');
            return Optional.of(new Problem("AtCoder", task,
                    underscore < 0 ? task : contest + " " + task.substring(underscore + 1).toUpperCase(Locale.ROOT)));
        }
        m = LEETCODE.matcher(url);
        if (m.find()) {
            String slug = m.group(1).toLowerCase(Locale.ROOT);
            StringBuilder title = new StringBuilder();
            for (String word : slug.split("-")) {
                if (!word.isEmpty()) title.append(title.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return Optional.of(new Problem("LeetCode", slug, title.toString()));
        }
        return Optional.empty();
    }

    /**
     * The problem's level on one scale for every judge: its own "level" property when set; else
     * its difficulty, read as its judge means it (a Codeforces or AtCoder rating, LeetCode's
     * Easy / Medium / Hard); else, for AtCoder, the letter of the problem in its contest.
     */
    static String level(Note note) {
        String own = note.get("level");
        if (!own.isEmpty()) return own;
        String judge = note.get("judge"), difficulty = note.get("difficulty").strip();
        for (String level : LEVELS) if (level.equalsIgnoreCase(difficulty)) return level;
        Integer rating = rating(difficulty);
        if (rating != null) {
            int[] from = judge.equalsIgnoreCase("AtCoder") ? new int[]{800, 1600, 2400} : new int[]{1200, 1600, 2100};
            return LEVELS.get(rating < from[0] ? 0 : rating < from[1] ? 1 : rating < from[2] ? 2 : 3);
        }
        Matcher m = ATCODER_TASK.matcher(note.get("id"));
        if (!judge.equalsIgnoreCase("AtCoder") || !m.find()) return "";
        int letter = Character.toLowerCase(m.group(2).charAt(0)) - 'a';
        int[] from = switch (m.group(1).toLowerCase(Locale.ROOT)) {   // the letters where Medium, Hard and Very hard start
            case "abc" -> new int[]{2, 4, 6};   // A-B Easy, C-D Medium, E-F Hard, G on Very hard
            case "arc" -> new int[]{0, 1, 3};   // A Medium, B-C Hard, D on Very hard
            default -> new int[]{0, 0, 1};      // AGC: A Hard, then Very hard
        };
        return LEVELS.get(letter < from[0] ? 0 : letter < from[1] ? 1 : letter < from[2] ? 2 : 3);
    }

    /** The difficulty's color as its judge paints it (rank colors, LeetCode's three); "" when unknown. */
    static String color(Note note) {
        Integer rating = rating(note.get("difficulty").strip());
        if (rating == null) return levelColor(level(note));
        if (note.get("judge").equalsIgnoreCase("AtCoder")) {
            return List.of("#808080", "#804000", "#008000", "#00A0A0", "#0000FF", "#A0A000", "#FF8000", "#FF0000").get(Math.min(rating / 400, 7));
        }
        return rating < 1200 ? "#808080" : rating < 1400 ? "#008000" : rating < 1600 ? "#03A89E" : rating < 1900 ? "#0000FF"
                : rating < 2100 ? "#AA00AA" : rating < 2400 ? "#FF8C00" : "#FF0000";
    }

    static String levelColor(String level) {
        return switch (level) {
            case "Easy" -> "#00A389";
            case "Medium" -> "#E8A200";
            case "Hard" -> "#EF2D56";
            case "Very hard" -> "#AA00AA";
            default -> "";
        };
    }

    private static Integer rating(String s) {
        return s.matches("\\d{1,4}") ? Integer.valueOf(s) : null;
    }
}
