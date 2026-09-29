package com.devavaxp.notes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorTest {

    @Test
    void theReadingViewNeverRunsWhatANoteSays() {
        String html = Editor.html("""
                <script>alert(1)</script>

                [site](javascript:alert(1)) <img src=x onerror=alert(1)>

                ```cpp
                int main() {}
                ```

                - [x] done

                | a | b |
                |---|---|
                | 1 | 2 |
                """);
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("javascript:"));
        assertTrue(html.contains("<code class=\"language-cpp\">"));
        assertTrue(html.contains("type=\"checkbox\""));
        assertTrue(html.contains("<table>"));
    }

    @Test
    void mathIsLeftForKatexButCodeStaysCode() {
        String html = Editor.html("""
                Area: $a_1 + b_1$, and on its own:

                $$
                \\frac{1}{2} < 1
                $$

                Not math: `$HOME`, $5 and $10, \\$x\\$.

                ```bash
                echo $PATH
                ```
                """);
        assertTrue(html.contains("<span class=\"math\">a_1 + b_1</span>"), html);
        assertTrue(html.contains("<span class=\"math display\">\n\\frac{1}{2} &lt; 1\n</span>"), html);
        assertTrue(html.contains("<code>$HOME</code>, $5 and $10, $x$."), html);
        assertTrue(html.contains("echo $PATH"), html);
        assertFalse(html.contains("<em>"), html);   // a_1 … b_1 did not turn into emphasis
    }
}
