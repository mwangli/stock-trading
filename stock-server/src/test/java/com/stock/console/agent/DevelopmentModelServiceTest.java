// AI_GENERATE_START ----
package com.stock.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.console.config.ConsoleProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentModelServiceTest {

    private final DevelopmentModelService service = new DevelopmentModelService(properties(), new ObjectMapper());

    @Test
    void replacesOnlyUniqueText() {
        assertEquals("before new after", service.replaceExactlyOnce(
                "before old after", "old", "new", "sample.txt"));
        assertThrows(IllegalArgumentException.class, () -> service.replaceExactlyOnce(
                "old and old", "old", "new", "sample.txt"));
    }

    @Test
    void incrementsExistingMarkers() {
        String original = "// AI_GENERATE_START -\nclass Sample {}\n// AI_GENERATE_END -";
        String generated = "// AI_GENERATE_START -\nclass Sample { int value; }\n// AI_GENERATE_END -";

        String result = service.maintainAiMarkers("Sample.java", original, generated);

        assertTrue(result.startsWith("// AI_GENERATE_START --\n"));
        assertTrue(result.endsWith("\n// AI_GENERATE_END --"));
    }

    @Test
    void addsMarkersToNewSourceFile() {
        String result = service.maintainAiMarkers("sample.ts", null, "export const value = 1;");

        assertEquals("// AI_GENERATE_START -\nexport const value = 1;\n// AI_GENERATE_END -", result);
    }

    private ConsoleProperties properties() {
        return new ConsoleProperties(
                Path.of("data"),
                Path.of("logs/tasks.log"),
                "documents",
                new ConsoleProperties.Auth("admin", "hash"),
                new ConsoleProperties.Agent(
                        true, "https://api.example.com", "key", "model", 90, 1800),
                new ConsoleProperties.GithubAgent(
                        true, "token", "owner", "repository", "stock-web", "ci-cd.yml", 2, 10, 45));
    }
}
// AI_GENERATE_END ----
