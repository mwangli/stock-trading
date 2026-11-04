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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentMemoryStore {

    private final AgentDatabase database;
    private final String ownerId;

    public AgentMemoryStore(AgentDatabase database, ConsoleProperties properties) {
        this.database = database;
        this.ownerId = properties.auth().username();
    }

    public Map<String, Object> remember(String category, String content) {
        String normalizedCategory = requireText(category, "记忆分类不能为空", 40);
        String normalizedContent = requireText(content, "记忆内容不能为空", 500);
        String now = Instant.now().toString();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO agent_memory(owner_id, category, content, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?)
                     """, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, ownerId);
            statement.setString(2, normalizedCategory);
            statement.setString(3, normalizedContent);
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0;
                return Map.of("id", id, "category", normalizedCategory, "content", normalizedContent, "createdAt", now);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("保存 Agent 长期记忆失败", exception);
        }
    }

    public List<Map<String, Object>> search(String query, int limit) {
        String normalized = requireText(query, "记忆查询不能为空", 200);
        int resolvedLimit = Math.max(1, Math.min(limit, 20));
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, category, content, created_at, updated_at
                     FROM agent_memory
                     WHERE owner_id = ? AND (category LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\')
                     ORDER BY updated_at DESC
                     LIMIT ?
                     """)) {
            String pattern = "%" + escapeLike(normalized) + "%";
            statement.setString(1, ownerId);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            statement.setInt(4, resolvedLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", resultSet.getLong("id"));
                    item.put("category", resultSet.getString("category"));
                    item.put("content", resultSet.getString("content"));
                    item.put("createdAt", resultSet.getString("created_at"));
                    item.put("updatedAt", resultSet.getString("updated_at"));
                    results.add(item);
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("搜索 Agent 长期记忆失败", exception);
        }
    }

    private String requireText(String value, String message, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(message.replace("不能为空", "不能超过 " + maximum + " 个字符"));
        }
        return normalized;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
// AI_GENERATE_END -
