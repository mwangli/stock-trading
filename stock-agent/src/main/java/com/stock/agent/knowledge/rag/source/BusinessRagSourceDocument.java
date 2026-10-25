// AI_GENERATE_START -
package com.stock.agent.knowledge.rag.source;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务系统提供给 RAG 自动摄取的标准文档。
 *
 * @param sourceType 来源类型
 * @param sourceId 来源稳定标识
 * @param content 可检索正文
 * @param stockCodes 关联股票代码
 * @param tags 业务标签
 * @param visibility 可见范围
 * @param userScope 用户范围，共享知识使用 system
 * @param asOf 业务事实时间
 * @author mwangli
 * @since 2026-10-25
 */
public record BusinessRagSourceDocument(
        String sourceType,
        String sourceId,
        String content,
        List<String> stockCodes,
        List<String> tags,
        String visibility,
        String userScope,
        LocalDateTime asOf) {
}
// AI_GENERATE_END -
