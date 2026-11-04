// AI_GENERATE_START --
package com.stock.console.agent;

import org.springframework.stereotype.Component;

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

    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public Map<String, Object> createSession(String title) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        SessionState session = new SessionState(id, resolveTitle(title), now);
        sessions.put(id, session);
        trimSessions();
        return session.view();
    }

    public List<Map<String, Object>> sessions() {
        return sessions.values().stream()
                .sorted(Comparator.comparing(SessionState::updatedAt).reversed())
                .limit(MAX_CONVERSATIONS)
                .map(SessionState::view)
                .toList();
    }

    public List<Map<String, Object>> messages(String sessionId) {
        return contextMessages(sessionId, MAX_MESSAGES_PER_CONVERSATION);
    }

    public List<Map<String, Object>> contextMessages(String sessionId, int limit) {
        SessionState session = requireSession(sessionId);
        synchronized (session) {
            int resolvedLimit = Math.max(1, Math.min(limit, MAX_MESSAGES_PER_CONVERSATION));
            int fromIndex = Math.max(0, session.messages.size() - resolvedLimit);
            return new ArrayList<>(session.messages.subList(fromIndex, session.messages.size()));
        }
    }

    public Object conversationLock(String sessionId) {
        return requireSession(sessionId);
    }

    public void appendMessage(String sessionId, String role, String content, String evidenceRefs) {
        SessionState session = requireSession(sessionId);
        synchronized (session) {
            String now = Instant.now().toString();
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("id", session.nextMessageId++);
            message.put("role", role);
            message.put("content", content);
            message.put("evidence_refs", evidenceRefs);
            message.put("created_at", now);
            session.messages.add(message);
            if (session.messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
                session.messages.remove(0);
            }
            session.updatedAt = now;
        }
    }

    private SessionState requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Agent 会话不能为空");
        }
        SessionState session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Agent 会话不存在或已失效");
        }
        return session;
    }

    private void trimSessions() {
        if (sessions.size() <= MAX_CONVERSATIONS) {
            return;
        }
        sessions.values().stream()
                .sorted(Comparator.comparing(SessionState::updatedAt))
                .limit(sessions.size() - MAX_CONVERSATIONS)
                .map(SessionState::id)
                .forEach(sessions::remove);
    }

    private String resolveTitle(String title) {
        String resolved = title == null || title.isBlank() ? "新对话" : title.trim();
        return resolved.substring(0, Math.min(MAX_TITLE_LENGTH, resolved.length()));
    }

    private static final class SessionState {
        private final String id;
        private final String title;
        private final String createdAt;
        private final List<Map<String, Object>> messages = new ArrayList<>();
        private long nextMessageId = 1;
        private volatile String updatedAt;

        private SessionState(String id, String title, String createdAt) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }

        private String id() {
            return id;
        }

        private String updatedAt() {
            return updatedAt;
        }

        private Map<String, Object> view() {
            return Map.of(
                    "id", id,
                    "title", title,
                    "createdAt", createdAt,
                    "updatedAt", updatedAt);
        }
    }
}
// AI_GENERATE_END --
