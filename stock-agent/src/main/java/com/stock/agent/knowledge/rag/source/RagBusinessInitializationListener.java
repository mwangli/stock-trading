// AI_GENERATE_START -
package com.stock.agent.knowledge.rag.source;

import com.stock.agent.knowledge.rag.embedding.LangChainEmbeddingService;
import com.stock.agent.shared.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent 启动后的业务知识 RAG 初始化入口。
 *
 * 该监听器在 Embedding 预热之后执行；依赖未就绪时保守跳过，不阻断 Agent 普通能力启动。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagBusinessInitializationListener {

    private final AgentProperties properties;
    private final LangChainEmbeddingService embeddingService;
    private final RagBusinessInitializationService initializationService;

    /**
     * 应用就绪后执行一次业务知识初始化。
     */
    @Order(200)
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (!properties.isEnabled() || !properties.isRagEnabled()
                || !properties.isRagAutoInitializationEnabled()) {
            return;
        }
        if (!embeddingService.status().isReady()) {
            log.warn("[RAG初始化] Embedding 未就绪，跳过本次自动初始化: status={}",
                    embeddingService.status().getStatus());
            return;
        }
        RagBusinessInitializationResult result = initializationService.initialize();
        log.info("[RAG初始化] 完成: sources={}, collected={}, changed={}, unchanged={}, failedSources={}",
                result.sourceCount(), result.collectedDocumentCount(), result.changedDocumentCount(),
                result.unchangedDocumentCount(), result.failedSourceCount());
    }
}
// AI_GENERATE_END -
