// AI_GENERATE_START -----
package com.stock.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "console")
public record ConsoleProperties(
        Path dataDir,
        Path taskLog,
        String documentRoots,
        Auth auth,
        Agent agent,
        GithubAgent githubAgent) {

    public Path tradingDb() {
        return dataDir.resolve("state/trading.db");
    }

    public Path marketDir() {
        return dataDir.resolve("market");
    }

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
            Path stateDir,
            String baseUrl,
            String apiKey,
            String model,
            long timeoutSeconds,
            int maxTokens) {

        public Agent(
                boolean enabled,
                String baseUrl,
                String apiKey,
                String model,
                long timeoutSeconds,
                int maxTokens) {
            this(enabled, Path.of("var/agent"), baseUrl, apiKey, model, timeoutSeconds, maxTokens);
        }
    }

    public record GithubAgent(
            boolean enabled,
            String token,
            String owner,
            String repository,
            String branch,
            String workflow,
            int maxRepairRounds,
            long pollIntervalSeconds,
            long workflowTimeoutMinutes) {
    }
}
// AI_GENERATE_END -----
