// AI_GENERATE_START -
package com.stock.console.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DevelopmentTaskStore {

    private final AgentDatabase database;
    private final ObjectMapper objectMapper;

    public DevelopmentTaskStore(AgentDatabase database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
    }

    public void save(StoredDevelopmentTask task) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO agent_development_task(task_id, status, payload_json, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?)
                     ON CONFLICT(task_id) DO UPDATE SET
                         status = excluded.status,
                         payload_json = excluded.payload_json,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setString(1, task.taskId());
            statement.setString(2, task.status().name());
            statement.setString(3, objectMapper.writeValueAsString(task));
            statement.setString(4, task.createdAt());
            statement.setString(5, task.updatedAt());
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException exception) {
            throw new IllegalStateException("开发任务状态保存失败", exception);
        }
    }

    public StoredDevelopmentTask find(String taskId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT payload_json FROM agent_development_task WHERE task_id = ?
                     """)) {
            statement.setString(1, taskId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? read(result.getString(1)) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("开发任务状态读取失败", exception);
        }
    }

    public List<StoredDevelopmentTask> recent(int limit) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT payload_json FROM agent_development_task
                     ORDER BY updated_at DESC LIMIT ?
                     """)) {
            statement.setInt(1, Math.max(1, Math.min(100, limit)));
            try (ResultSet result = statement.executeQuery()) {
                List<StoredDevelopmentTask> tasks = new ArrayList<>();
                while (result.next()) {
                    tasks.add(read(result.getString(1)));
                }
                return tasks;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("开发任务列表读取失败", exception);
        }
    }

    private StoredDevelopmentTask read(String json) {
        try {
            return objectMapper.readValue(json, StoredDevelopmentTask.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("开发任务状态解析失败", exception);
        }
    }

    public record StoredDevelopmentTask(
            String taskId,
            String sessionId,
            String requirement,
            DevelopmentTaskStatus status,
            int buildAttempt,
            List<String> changedFiles,
            String commitSha,
            Long workflowRunId,
            String workflowUrl,
            String progress,
            String failureMessage,
            String temporaryBranch,
            String targetHead,
            boolean cancelled,
            String createdAt,
            String updatedAt) {
    }
}
// AI_GENERATE_END -
