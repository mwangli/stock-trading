// AI_GENERATE_START --
package com.stock.console.agent;

import com.stock.console.config.ConsoleProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class AgentDatabase {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS agent_session (
                id TEXT PRIMARY KEY,
                owner_id TEXT NOT NULL,
                title TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_agent_session_owner_updated
                ON agent_session(owner_id, updated_at DESC);

            CREATE TABLE IF NOT EXISTS agent_message (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                evidence_refs TEXT,
                created_at TEXT NOT NULL,
                FOREIGN KEY(session_id) REFERENCES agent_session(id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_agent_message_session_id
                ON agent_message(session_id, id);

            CREATE TABLE IF NOT EXISTS agent_memory (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_id TEXT NOT NULL,
                category TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_agent_memory_owner_updated
                ON agent_memory(owner_id, updated_at DESC);

            CREATE TABLE IF NOT EXISTS agent_tool_audit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT,
                tool_name TEXT NOT NULL,
                arguments_summary TEXT,
                result_status TEXT NOT NULL,
                source_ref TEXT,
                created_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_agent_tool_audit_session_id
                ON agent_tool_audit(session_id, id);

            CREATE TABLE IF NOT EXISTS agent_development_task (
                task_id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_agent_development_task_updated
                ON agent_development_task(updated_at DESC);
            """;

    private final Path databasePath;

    public AgentDatabase(ConsoleProperties properties) {
        this.databasePath = properties.agent().stateDir().resolve("agent.db").toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                for (String sql : SCHEMA.split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
            }
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Agent 状态数据库初始化失败", exception);
        }
    }

    Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA journal_mode=WAL");
        }
        return connection;
    }

    public String sourceRef() {
        return "agent-state:sqlite";
    }
}
// AI_GENERATE_END --
