// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 券商只读事实同步结果。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerSyncResultDto {

    /** 同步操作名称。 */
    private String operation;

    /** 同步开始日期。 */
    private LocalDate startDate;

    /** 同步结束日期。 */
    private LocalDate endDate;

    /** 计划同步的窗口数量。 */
    private int totalWindows;

    /** 成功完成的窗口数量。 */
    private int successfulWindows;

    /** 因已有成功批次而跳过的窗口数量。 */
    private int skippedWindows;

    /** 同步失败的窗口数量。 */
    private int failedWindows;

    /** 从券商接收的记录数量。 */
    private int receivedCount;

    /** 新增记录数量。 */
    private int insertedCount;

    /** 更新记录数量。 */
    private int updatedCount;

    /** 因字段不完整而跳过的记录数量。 */
    private int skippedRecordCount;

    /** 整体同步是否没有失败窗口。 */
    private boolean success;

    /** 同步结果摘要。 */
    private String message;
}
// AI_GENERATE_END --
