// AI_GENERATE_START ---
package com.stock.console.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class AgentToolAudit {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolAudit.class);
    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker masker;
    private final ThreadLocal<CallContext> callContext = new ThreadLocal<>();

    public AgentToolAudit(ObjectMapper objectMapper, SensitiveDataMasker masker) {
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
        LOGGER.info("Agent tool session={} tool={} source={} arguments={}",
                current == null ? "" : current.sessionId(), toolName, source, masker.mask(json(arguments)));
    }

    public List<String> finish() {
        CallContext current = callContext.get();
        callContext.remove();
        return current == null ? List.of() : new ArrayList<>(current.sources());
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
// AI_GENERATE_END ---
