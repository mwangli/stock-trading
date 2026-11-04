// AI_GENERATE_START -
package com.stock.console.agent;

import com.stock.console.config.ConsoleProperties;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentConversationStore {

    static final int MAX_CONVERSATIONS = 20;
    static final int MAX_MESSAGES_PER_CONVERSATION = 200;
    private static final int MAX_TITLE_LENGTH = 80;

    private final AgentDatabase database;
    private final String ownerId;
    private final ConcurrentHashMap<String, Object> conversationLocks = new ConcurrentHashMap<>();

    public AgentConversationStore(AgentDatabase database, ConsoleProperties properties) {
        this.database = database;
        this.ownerId = properties.auth().username();
    }

    public Map<String, Object> createSession(String title) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO agent_session(id, owner_id, title, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, id);
                statement.setString(2, ownerId);
                statement.setString(3, resolveTitle(title));
                statement.setString(4, now);
                statement.setString(5, now);
                statement.executeUpdate();
            }
            trimSessions(connection);
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("创建 Agent 会话失败", exception);
        }
        return Map.of("id", id, "title", resolveTitle(title), "createdAt", now, "updatedAt", now);
    }

    public List<Map<String, Object>> sessions() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, title, created_at, updated_at
                     FROM agent_session
                     WHERE owner_id = ?
                     ORDER BY updated_at DESC
                     LIMIT ?
                     """)) {
            statement.setString(1, ownerId);
            statement.setInt(2, MAX_CONVERSATIONS);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> sessions = new ArrayList<>();
                while (resultSet.next()) {
                    sessions.add(Map.of(
                            "id", resultSet.getString("id"),
                            "title", resultSet.getString("title"),
                            "created_at", resultSet.getString("created_at"),
                            "updated_at", resultSet.getString("updated_at")));
                }
                return sessions;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取 Agent 会话失败", exception);
        }
    }

    public List<Map<String, Object>> messages(String sessionId) {
        return messages(sessionId, MAX_MESSAGES_PER_CONVERSATION);
    }

    public List<Map<String, Object>> contextMessages(String sessionId, int limit) {
        return messages(sessionId, Math.max(1, Math.min(limit, MAX_MESSAGES_PER_CONVERSATION)));
    }

    public Object conversationLock(String sessionId) {
        requireSession(sessionId);
        return conversationLocks.computeIfAbsent(sessionId, ignored -> new Object());
    }

    public void appendMessage(String sessionId, String role, String content, String evidenceRefs) {
        requireSession(sessionId);
        String now = Instant.now().toString();
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO agent_message(session_id, role, content, evidence_refs, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, sessionId);
                insert.setString(2, role);
                insert.setString(3, content);
                insert.setString(4, evidenceRefs);
                insert.setString(5, now);
                insert.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE agent_session SET updated_at = ? WHERE id = ? AND owner_id = ?
                    """)) {
                update.setString(1, now);
                update.setString(2, sessionId);
                update.setString(3, ownerId);
                update.executeUpdate();
            }
            trimMessages(connection, sessionId);
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("保存 Agent 消息失败", exception);
        }
    }

    private List<Map<String, Object>> messages(String sessionId, int limit) {
        requireSession(sessionId);
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, role, content, evidence_refs, created_at
                     FROM (
                         SELECT id, role, content, evidence_refs, created_at
                         FROM agent_message
                         WHERE session_id = ?
                         ORDER BY id DESC
                         LIMIT ?
                     ) recent
                     ORDER BY id
                     """)) {
            statement.setString(1, sessionId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> messages = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", resultSet.getLong("id"));
                    item.put("role", resultSet.getString("role"));
                    item.put("content", resultSet.getString("content"));
                    item.put("evidence_refs", resultSet.getString("evidence_refs"));
                    item.put("created_at", resultSet.getString("created_at"));
                    messages.add(item);
                }
                return messages;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取 Agent 消息失败", exception);
        }
    }

    private void requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Agent 会话不能为空");
        }
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM agent_session WHERE id = ? AND owner_id = ?
                     """)) {
            statement.setString(1, sessionId);
            statement.setString(2, ownerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Agent 会话不存在或已失效");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("校验 Agent 会话失败", exception);
        }
    }

    private void trimSessions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM agent_session
                WHERE owner_id = ? AND id NOT IN (
                    SELECT id FROM agent_session
                    WHERE owner_id = ?
                    ORDER BY updated_at DESC
                    LIMIT ?
                )
                """)) {
            statement.setString(1, ownerId);
            statement.setString(2, ownerId);
            statement.setInt(3, MAX_CONVERSATIONS);
            statement.executeUpdate();
        }
    }

    private void trimMessages(Connection connection, String sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM agent_message
                WHERE session_id = ? AND id NOT IN (
                    SELECT id FROM agent_message
                    WHERE session_id = ?
                    ORDER BY id DESC
                    LIMIT ?
                )
                """)) {
            statement.setString(1, sessionId);
            statement.setString(2, sessionId);
            statement.setInt(3, MAX_MESSAGES_PER_CONVERSATION);
            statement.executeUpdate();
        }
    }

    private String resolveTitle(String title) {
        String resolved = title == null || title.isBlank() ? "新对话" : title.trim();
        return resolved.substring(0, Math.min(MAX_TITLE_LENGTH, resolved.length()));
    }
}
// AI_GENERATE_END -
