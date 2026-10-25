// AI_GENERATE_START -
package com.stock.agent.knowledge.rag.source;

import java.util.List;

/**
 * RAG 业务知识来源契约。
 *
 * 每个实现只负责从一个受控业务来源生成标准文档，不直接写 MongoDB。
 *
 * @author mwangli
 * @since 2026-10-25
 */
public interface BusinessRagSource {

    /**
     * 返回来源名称，用于初始化日志和失败定位。
     *
     * @return 来源名称
     */
    String sourceName();

    /**
     * 采集当前可进入 RAG 的标准文档。
     *
     * @return 待同步文档列表
     */
    List<BusinessRagSourceDocument> collect();
}
// AI_GENERATE_END -
