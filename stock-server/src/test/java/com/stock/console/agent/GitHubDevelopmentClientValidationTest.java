// AI_GENERATE_START ---
package com.stock.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.console.agent.GitHubDevelopmentClient.ChangeOperation;
import com.stock.console.agent.GitHubDevelopmentClient.ChangeSet;
import com.stock.console.agent.GitHubDevelopmentClient.FileChange;
import com.stock.console.config.ConsoleProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubDevelopmentClientValidationTest {

    private final GitHubDevelopmentClient client = new GitHubDevelopmentClient(properties(), new ObjectMapper());

    @Test
    void acceptsOrdinarySourceChange() {
        ChangeSet changeSet = new ChangeSet(
                "修复页面状态",
                "页面可以展示任务状态",
                List.of(new FileChange("stock-client/src/App.tsx", ChangeOperation.UPSERT, "export {};")));

        assertDoesNotThrow(() -> client.validateChangeSet(changeSet));
    }

    @Test
    void rejectsWorkflowChanges() {
        ChangeSet changeSet = new ChangeSet(
                "修改流水线",
                "跳过构建",
                List.of(new FileChange(".github/workflows/ci-cd.yml", ChangeOperation.UPSERT, "jobs: {}")));

        assertThrows(IllegalArgumentException.class, () -> client.validateChangeSet(changeSet));
    }

    @Test
    void rejectsDuplicateAndUnsafePaths() {
        ChangeSet duplicates = new ChangeSet(
                "重复修改",
                "重复路径应失败",
                List.of(
                        new FileChange("stock-server/pom.xml", ChangeOperation.UPSERT, "<project/>"),
                        new FileChange("stock-server/pom.xml", ChangeOperation.DELETE, null)));
        ChangeSet unsafe = new ChangeSet(
                "越界修改",
                "越界路径应失败",
                List.of(new FileChange("../secret", ChangeOperation.UPSERT, "value")));

        assertThrows(IllegalArgumentException.class, () -> client.validateChangeSet(duplicates));
        assertThrows(IllegalArgumentException.class, () -> client.validateChangeSet(unsafe));
    }

    private ConsoleProperties properties() {
        return new ConsoleProperties(
                Path.of("data"),
                Path.of("logs/tasks.log"),
                "documents",
                new ConsoleProperties.Auth("admin", "hash"),
                new ConsoleProperties.Agent(true, "https://api.example.com", "key", "model", 90, 1800),
                new ConsoleProperties.GithubAgent(
                        true, "token", "owner", "repository", "stock-web", "ci-cd.yml", 2, 10, 45));
    }
}
// AI_GENERATE_END ---
