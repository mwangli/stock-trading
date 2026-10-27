// AI_GENERATE_START --
package com.stock.agent.model;

import java.util.List;

/**
 * 业务知识初始化汇总结果。
 *
 * @param sourceCount 已执行来源数量
 * @param collectedDocumentCount 采集文档数量
 * @param changedDocumentCount 新增或更新文档数量
 * @param unchangedDocumentCount 内容未变化文档数量
 * @param failedSourceCount 失败来源数量
 * @param errors 脱敏失败摘要
 * @author mwangli
 * @since 2026-10-25
 */
public record RagBusinessInitializationResult(
        int sourceCount,
        int collectedDocumentCount,
        int changedDocumentCount,
        int unchangedDocumentCount,
        int failedSourceCount,
        List<String> errors) {
}
// AI_GENERATE_END --