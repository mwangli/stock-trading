// AI_GENERATE_START --
package com.stock.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "console")
public record ConsoleProperties(
        Path tradingDb,
        Path marketDir,
        Path taskLog,
        String documentRoots,
        int expectedContractVersion,
        Auth auth,
        Agent agent) {

    public List<Path> resolvedDocumentRoots() {
        return Arrays.stream(documentRoots.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Path::of)
                .toList();
    }

    public record Auth(String username, String passwordHash) {
    }

    public record Agent(
            boolean enabled,
            String baseUrl,
            String apiKey,
            String model,
            long timeoutSeconds,
            int maxTokens) {
    }
}
// AI_GENERATE_END --
