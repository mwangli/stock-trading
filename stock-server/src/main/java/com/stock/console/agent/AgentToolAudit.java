// AI_GENERATE_START --
package com.stock.console.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class AgentToolAudit {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolAudit.class);
    private final AgentDatabase database;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker masker;
    private final ThreadLocal<CallContext> callContext = new ThreadLocal<>();

    public AgentToolAudit(AgentDatabase database, ObjectMapper objectMapper, SensitiveDataMasker masker) {
        this.database = database;
        this.objectMapper = objectMapper;
        this.masker = masker;
    }

    public void begin(String sessionId) {
        callContext.set(new CallContext(sessionId, new LinkedHashSet<>()));
    }

    public void record(String toolName, Map<String, Object> arguments, String source) {
        CallContext current = callContext.get();
        if (current != null) {
            current.sources().add(source);
        }
        persist(current == null ? null : current.sessionId(), toolName, arguments, "COMPLETED", source);
    }

    public List<String> finish() {
        CallContext current = callContext.get();
        callContext.remove();
        return current == null ? List.of() : new ArrayList<>(current.sources());
    }

    private void persist(
            String sessionId,
            String toolName,
            Map<String, Object> arguments,
            String status,
            String source) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO agent_tool_audit(
                         session_id, tool_name, arguments_summary, result_status, source_ref, created_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, sessionId);
            statement.setString(2, toolName);
            statement.setString(3, masker.mask(json(arguments)));
            statement.setString(4, status);
            statement.setString(5, source);
            statement.setString(6, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Agent 工具审计写入失败: {}", exception.getClass().getSimpleName());
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private record CallContext(String sessionId, LinkedHashSet<String> sources) {
    }
}
// AI_GENERATE_END --
