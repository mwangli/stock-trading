// AI_GENERATE_START ---
package com.stock.agent.service;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.dto.RagResult;
import com.stock.agent.dto.RagSearchRequest;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.model.EvidenceBundle;
import com.stock.agent.model.EvidenceItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 将自动 RAG 检索结果转换为本轮 Agent 可引用的背景证据。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentKnowledgeContextService {

    private static final int MAX_RESULTS = 5;
    private static final int MAX_CHUNK_CHARS = 2000;
    private static final int MAX_SUMMARY_CHARS = 8000;

    private final AgentRagService ragService;
    private final LangChainEmbeddingService embeddingService;
    private final AgentProperties properties;

    /**
     * 根据 REQUIRED API 契约强制检索 RAG，并转换为受控 Evidence。
     *
     * @param question 用户问题
     * @param userScope 服务端认证用户范围
     * @return 本轮知识证据；失败或无匹配时返回明确 UNKNOWN Evidence
     */
    public EvidenceBundle retrieveRequired(String question, String userScope) {
        if (!properties.isRagEnabled() || !embeddingService.status().isReady()) {
            return unavailable("RAG_NOT_READY", "用户明确要求检索知识库，但 RAG 或 Embedding 当前未就绪");
        }
        try {
            List<RagResult> results = search(question, userScope);
            if (results.isEmpty()) {
                return unavailable("RAG_NO_MATCH", "知识库中没有找到与当前问题匹配的资料");
            }
            List<EvidenceItem> items = new ArrayList<>();
            for (int index = 0; index < results.size(); index++) {
                RagResult result = results.get(index);
                items.add(EvidenceItem.builder()
                        .evidenceId("RAG" + String.format("%03d", index + 1))
                        .sourceType("RAG:" + result.getSourceType())
                        .sourceId(result.getSourceId())
                        .sourceVersion(result.getVersion())
                        .asOf(result.getAsOf())
                        .status("AVAILABLE")
                        .summary(limit(result.getContent(), MAX_CHUNK_CHARS))
                        .build());
            }
            List<String> refs = items.stream().map(EvidenceItem::getEvidenceId).toList();
            String summary = limit(items.stream().map(JSON::toJSONString)
                    .reduce("", (left, right) -> left + right + "\n"), MAX_SUMMARY_CHARS);
            log.info("[Agent][RAG] mode=REQUIRED status=COMPLETED resultCount={} evidenceRefCount={}",
                    results.size(), refs.size());
            return EvidenceBundle.builder()
                    .summary(summary)
                    .items(List.copyOf(items))
                    .evidenceRefs(refs)
                    .build();
        } catch (RuntimeException exception) {
            log.warn("[Agent][RAG] mode=REQUIRED status=FAILED causeType={}",
                    exception.getClass().getSimpleName());
            return unavailable("RAG_SEARCH_FAILED", "用户明确要求检索知识库，但本次检索失败");
        }
    }

    /**
     * 在服务端用户范围内执行一次受限 RAG 检索。
     *
     * @param question 检索问题
     * @param userScope 服务端认证用户范围
     * @return 最多五个匹配 Chunk
     */
    public List<RagResult> search(String question, String userScope) {
        if (!properties.isRagEnabled() || !embeddingService.status().isReady()) {
            throw new IllegalStateException("RAG_NOT_READY");
        }
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery(question);
        request.setLimit(MAX_RESULTS);
        return ragService.search(request, userScope);
    }

    private EvidenceBundle unavailable(String errorCode, String summary) {
        EvidenceItem item = EvidenceItem.builder()
                .evidenceId("RAG000")
                .sourceType("RAG")
                .sourceId("knowledge-base")
                .sourceVersion(embeddingService.modelVersion())
                .asOf(LocalDateTime.now())
                .status("UNKNOWN")
                .summary(summary)
                .errorCode(errorCode)
                .build();
        return EvidenceBundle.builder()
                .summary(JSON.toJSONString(item))
                .items(List.of(item))
                .evidenceRefs(List.of())
                .build();
    }

    private String limit(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
// AI_GENERATE_END ---