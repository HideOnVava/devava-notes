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
}
