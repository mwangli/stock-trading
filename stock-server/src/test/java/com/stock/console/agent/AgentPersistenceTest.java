// AI_GENERATE_START -
package com.stock.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.console.agent.DevelopmentTaskStore.StoredDevelopmentTask;
import com.stock.console.config.ConsoleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPersistenceTest {

    @TempDir
    Path tempDir;

    private AgentDatabase database;
    private ConsoleProperties properties;

    @BeforeEach
    void setUp() {
        properties = properties();
        database = new AgentDatabase(properties);
        database.initialize();
    }

    @Test
    void persistsConversationAndExplicitMemoryAcrossStoreInstances() {
        AgentConversationStore conversations = new AgentConversationStore(database, properties);
        String sessionId = String.valueOf(conversations.createSession("收益口径").get("id"));
        conversations.appendMessage(sessionId, "user", "只看净收益", null);
        new AgentMemoryStore(database, properties).remember("return-basis", "只看净收益");

        AgentConversationStore restored = new AgentConversationStore(database, properties);

        assertEquals("只看净收益", restored.messages(sessionId).get(0).get("content"));
        assertEquals("只看净收益",
                new AgentMemoryStore(database, properties).search("净收益", 10).get(0).get("content"));
    }

    @Test
    void persistsDevelopmentTaskState() {
        DevelopmentTaskStore tasks = new DevelopmentTaskStore(database, new ObjectMapper());
        StoredDevelopmentTask saved = new StoredDevelopmentTask(
                "task-1", "session-1", "修复失败任务", DevelopmentTaskStatus.VERIFYING,
                1, List.of("stock-server/src/App.java"), "abc123", 99L, "https://actions/99",
                "正在验证", null, "agent/fix-task-1", "base123", false,
                "2026-11-04T01:00:00Z", "2026-11-04T01:01:00Z");

        tasks.save(saved);

        assertEquals(saved, tasks.find("task-1"));
        assertEquals(List.of(saved), tasks.recent(20));
    }

    @Test
    void masksCredentialsWithoutChangingOrdinaryBusinessValues() {
        SensitiveDataMasker masker = new SensitiveDataMasker();

        String masked = masker.mask("token=ghp_abcdefghijklmnopqrstuvwxyz password: secret123 net_profit=150");

        assertFalse(masked.contains("ghp_abcdefghijklmnopqrstuvwxyz"));
        assertFalse(masked.contains("secret123"));
        assertTrue(masked.contains("net_profit=150"));
    }

    private ConsoleProperties properties() {
        return new ConsoleProperties(
                Path.of("data"),
                Path.of("logs/tasks.log"),
                "documents",
                new ConsoleProperties.Auth("admin", "hash"),
                new ConsoleProperties.Agent(
                        true, tempDir, "https://api.example.com", "key", "model", 90, 1800),
                new ConsoleProperties.GithubAgent(
                        false, "", "", "", "stock-web", "ci-cd.yml", 2, 2, 5));
    }
}
// AI_GENERATE_END -
