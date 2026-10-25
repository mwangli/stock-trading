// AI_GENERATE_START -
package com.stock.agent.knowledge.rag.source;

import com.stock.agent.knowledge.rag.service.AgentRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排业务系统知识的 RAG 初始化与幂等重跑。
 *
 * 单个来源失败不会阻断其他来源，最终返回可审计的成功、跳过和失败数量。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagBusinessInitializationService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 200;

    private final List<BusinessRagSource> sources;
    private final AgentRagService ragService;

    /**
     * 执行全部已注册业务来源的初始化或增量重跑。
     *
     * @return 初始化汇总结果
     */
    public RagBusinessInitializationResult initialize() {
        int collected = 0;
        int changed = 0;
        int unchanged = 0;
        int failedSources = 0;
        List<String> errors = new ArrayList<>();
        for (BusinessRagSource source : sources) {
            try {
                List<BusinessRagSourceDocument> documents = source.collect();
                collected += documents.size();
                for (BusinessRagSourceDocument document : documents) {
                    RagSourceSyncResult result = ragService.synchronizeBusinessSource(document);
                    if (result.changed()) {
                        changed++;
                    } else {
                        unchanged++;
                    }
                }
                log.info("[RAG初始化] 业务来源完成: source={}, documents={}",
                        source.sourceName(), documents.size());
            } catch (RuntimeException exception) {
                failedSources++;
                String error = source.sourceName() + ": " + sanitize(exception.getMessage());
                errors.add(error);
                log.warn("[RAG初始化] 业务来源失败: source={}, reason={}",
                        source.sourceName(), sanitize(exception.getMessage()));
            }
        }
        return new RagBusinessInitializationResult(
                sources.size(), collected, changed, unchanged, failedSources, List.copyOf(errors));
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        String normalized = message.replaceAll("(?i)(token|password|secret|cookie)=[^,\\s]+", "$1=[REDACTED]");
        return normalized.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? normalized : normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
// AI_GENERATE_END -
