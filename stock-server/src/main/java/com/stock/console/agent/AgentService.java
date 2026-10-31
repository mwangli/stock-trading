// AI_GENERATE_START ----
package com.stock.console.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.console.config.ConsoleProperties;
import com.stock.console.documents.DocumentService;
import com.stock.console.facts.TradingFacts;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    static final int MAX_QUESTION_LENGTH = 4_000;

    private static final String SYSTEM_PROMPT = """
            你是纯 Python T+1 交易系统的只读运营助手。
            只能根据提供的 SQLite 事实和 Markdown 文档回答，不得声称已经执行交易、训练、调度或数据修复。
            必须区分源码说明、当前文件事实和缺失信息。答案使用中文，结论简洁，并列出主要证据来源。
            """;

    private final ConsoleProperties properties;
    private final InMemoryConversationStore store;
    private final TradingFacts facts;
    private final DocumentService documents;
    private final ObjectMapper objectMapper;
    private volatile ConsoleAssistant assistant;

    public AgentService(
            ConsoleProperties properties,
            InMemoryConversationStore store,
            TradingFacts facts,
            DocumentService documents,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.store = store;
        this.facts = facts;
        this.documents = documents;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> createSession(HttpSession httpSession, String title) {
        return store.createSession(httpSession, title);
    }

    public List<Map<String, Object>> sessions(HttpSession httpSession) {
        return store.sessions(httpSession);
    }

    public List<Map<String, Object>> history(HttpSession httpSession, String sessionId) {
        return store.messages(httpSession, sessionId);
    }

    public Map<String, Object> chat(HttpSession httpSession, String sessionId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        String trimmedQuestion = question.trim();
        if (trimmedQuestion.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("问题不能超过 " + MAX_QUESTION_LENGTH + " 个字符");
        }
        String resolvedSession = sessionId;
        if (resolvedSession == null || resolvedSession.isBlank()) {
            resolvedSession = String.valueOf(createSession(
                    httpSession,
                    trimmedQuestion.substring(0, Math.min(30, trimmedQuestion.length()))).get("id"));
        }
        synchronized (store.conversationLock(httpSession, resolvedSession)) {
            return execute(httpSession, resolvedSession, trimmedQuestion);
        }
    }

    public Map<String, Object> status() {
        ConsoleProperties.Agent config = properties.agent();
        return Map.of(
                "enabled", config.enabled(),
                "configured", config.apiKey() != null && !config.apiKey().isBlank(),
                "model", config.model());
    }

    private Map<String, Object> execute(HttpSession httpSession, String sessionId, String question) {
        store.appendMessage(httpSession, sessionId, "user", question, null);
        List<DocumentService.SearchHit> hits = documents.search(question, 5);
        List<String> evidence = new ArrayList<>();
        evidence.add("duckdb:sqlite+parquet:read-only");
        hits.forEach(hit -> evidence.add("document:" + hit.path()));
        String answer;
        if (!properties.agent().enabled() || properties.agent().apiKey() == null
                || properties.agent().apiKey().isBlank()) {
            answer = "Agent Provider 未配置。当前已读取交易事实和文档索引，但不能生成模型回答。";
        } else {
            Result<String> result = assistant().chat(prompt(httpSession, sessionId, question, hits), SYSTEM_PROMPT);
            answer = result.content();
        }
        String refs = json(evidence);
        store.appendMessage(httpSession, sessionId, "assistant", answer, refs);
        return Map.of(
                "sessionId", sessionId,
                "answer", answer,
                "evidenceRefs", evidence,
                "createdAt", Instant.now().toString());
    }

    private String prompt(
            HttpSession httpSession,
            String sessionId,
            String question,
            List<DocumentService.SearchHit> hits) {
        List<Map<String, Object>> history = store.messages(httpSession, sessionId);
        List<Map<String, Object>> recent = history.subList(Math.max(0, history.size() - 10), history.size());
        return "用户问题：\n" + question
                + "\n\n最近对话：\n" + json(recent)
                + "\n\n只读业务事实：\n" + json(facts.agentSnapshot())
                + "\n\n文档检索结果：\n" + json(hits);
    }

    private ConsoleAssistant assistant() {
        ConsoleAssistant current = assistant;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (assistant == null) {
                ConsoleProperties.Agent config = properties.agent();
                OpenAiChatModel model = OpenAiChatModel.builder()
                        .baseUrl(config.baseUrl())
                        .apiKey(config.apiKey())
                        .modelName(config.model())
                        .maxTokens(Math.max(1, config.maxTokens()))
                        .timeout(Duration.ofSeconds(Math.max(1, config.timeoutSeconds())))
                        .maxRetries(0)
                        .build();
                assistant = AiServices.builder(ConsoleAssistant.class).chatModel(model).build();
            }
            return assistant;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }
}
// AI_GENERATE_END ----
