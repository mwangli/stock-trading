// AI_GENERATE_START --
package com.stock.console.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsolePropertiesBindingTest {

    @Test
    void bindsAgentConfigurationWithStateDirectory() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("console.data-dir", "data"),
                Map.entry("console.task-log", "logs/tasks.log"),
                Map.entry("console.document-roots", "documents"),
                Map.entry("console.auth.username", "admin"),
                Map.entry("console.auth.password-hash", "hash"),
                Map.entry("console.agent.enabled", "true"),
                Map.entry("console.agent.state-dir", "/var/lib/stock-console"),
                Map.entry("console.agent.base-url", "https://api.example.com"),
                Map.entry("console.agent.api-key", "key"),
                Map.entry("console.agent.model", "model"),
                Map.entry("console.agent.timeout-seconds", "90"),
                Map.entry("console.agent.max-tokens", "1800"),
                Map.entry("console.github-agent.enabled", "false"),
                Map.entry("console.github-agent.owner", "owner"),
                Map.entry("console.github-agent.repository", "repository"),
                Map.entry("console.github-agent.branch", "stock-web"),
                Map.entry("console.github-agent.workflow", "ci-cd.yml"),
                Map.entry("console.github-agent.max-repair-rounds", "2"),
                Map.entry("console.github-agent.poll-interval-seconds", "10"),
                Map.entry("console.github-agent.workflow-timeout-minutes", "45")));

        ConsoleProperties properties = new Binder(source)
                .bind("console", Bindable.of(ConsoleProperties.class))
                .orElseThrow(() -> new IllegalStateException("console properties did not bind"));

        assertNotNull(properties.agent());
        assertEquals(Path.of("/var/lib/stock-console"), properties.agent().stateDir());
    }
}
// AI_GENERATE_END --
