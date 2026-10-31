// AI_GENERATE_START ---
package com.stock.console.agent;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class InMemoryConversationStore {

    static final int MAX_CONVERSATIONS = 20;
    static final int MAX_MESSAGES_PER_CONVERSATION = 50;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final String SESSION_ATTRIBUTE = InMemoryConversationStore.class.getName() + ".conversations";
    private final Object sessionInitializationLock = new Object();

    public Map<String, Object> createSession(HttpSession httpSession, String title) {
        Conversation conversation = new Conversation(resolveTitle(title));
        SessionState state = state(httpSession);
        synchronized (state) {
            if (state.conversations().size() >= MAX_CONVERSATIONS) {
                state.conversations().values().stream()
                        .min(Comparator.comparing(Conversation::updatedAt))
                        .ifPresent(oldest -> state.conversations().remove(oldest.id()));
            }
            state.conversations().put(conversation.id(), conversation);
        }
        return conversation.creationView();
    }

    public List<Map<String, Object>> sessions(HttpSession httpSession) {
        List<Conversation> snapshot;
        SessionState state = state(httpSession);
        synchronized (state) {
            snapshot = new ArrayList<>(state.conversations().values());
        }
        return snapshot.stream()
                .sorted(Comparator.comparing(Conversation::updatedAt).reversed())
                .map(Conversation::sessionView)
                .toList();
    }

    public List<Map<String, Object>> messages(HttpSession httpSession, String sessionId) {
        Conversation conversation = conversation(httpSession, sessionId);
        synchronized (conversation) {
            return conversation.messages().stream().map(Message::view).toList();
        }
    }

    public Object conversationLock(HttpSession httpSession, String sessionId) {
        return conversation(httpSession, sessionId);
    }

    public void appendMessage(
            HttpSession httpSession,
            String sessionId,
            String role,
            String content,
            String evidenceRefs) {
        Conversation conversation = conversation(httpSession, sessionId);
        synchronized (conversation) {
            conversation.append(role, content, evidenceRefs);
        }
    }

    private SessionState state(HttpSession httpSession) {
        Object existing = httpSession.getAttribute(SESSION_ATTRIBUTE);
        if (existing instanceof SessionState state) {
            return state;
        }
        synchronized (sessionInitializationLock) {
            existing = httpSession.getAttribute(SESSION_ATTRIBUTE);
            if (existing instanceof SessionState state) {
                return state;
            }
            SessionState created = new SessionState(new LinkedHashMap<>());
            httpSession.setAttribute(SESSION_ATTRIBUTE, created);
            return created;
        }
    }

    private Conversation conversation(HttpSession httpSession, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Agent 会话不能为空");
        }
        SessionState state = state(httpSession);
        synchronized (state) {
            Conversation conversation = state.conversations().get(sessionId);
            if (conversation == null) {
                throw new IllegalArgumentException("Agent 会话不存在或已失效");
            }
            return conversation;
        }
    }

    private record SessionState(Map<String, Conversation> conversations) {
    }

    private String resolveTitle(String title) {
        String resolved = title == null || title.isBlank() ? "新对话" : title.trim();
        return resolved.substring(0, Math.min(MAX_TITLE_LENGTH, resolved.length()));
    }

    private static final class Conversation {
        private final String id = UUID.randomUUID().toString();
        private final String title;
        private final String createdAt = Instant.now().toString();
        private final List<Message> messages = new ArrayList<>();
        private volatile String updatedAt = createdAt;
        private long nextMessageId = 1;

        private Conversation(String title) {
            this.title = title;
        }

        private String id() {
            return id;
        }

        private String updatedAt() {
            return updatedAt;
        }

        private List<Message> messages() {
            return messages;
        }

        private void append(String role, String content, String evidenceRefs) {
            messages.add(new Message(nextMessageId++, role, content, evidenceRefs, Instant.now().toString()));
            while (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
                messages.remove(0);
            }
            updatedAt = Instant.now().toString();
        }

        private Map<String, Object> creationView() {
            return Map.of("id", id, "title", title, "createdAt", createdAt, "updatedAt", updatedAt());
        }

        private Map<String, Object> sessionView() {
            return Map.of("id", id, "title", title, "created_at", createdAt, "updated_at", updatedAt());
        }
    }

    private record Message(long id, String role, String content, String evidenceRefs, String createdAt) {

        private Map<String, Object> view() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("role", role);
            result.put("content", content);
            result.put("evidence_refs", evidenceRefs);
            result.put("created_at", createdAt);
            return result;
        }
    }
}
// AI_GENERATE_END ---
