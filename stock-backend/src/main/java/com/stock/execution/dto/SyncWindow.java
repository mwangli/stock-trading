// AI_GENERATE_START----
package com.stock.execution.dto;

import java.time.LocalDate;

/**
 * 一个同步窗口，表示某个数据集的一段业务时间范围。
 *
 * @author mwangli
 * @since 2026-10-16
 */
public record SyncWindow(String dataset, LocalDate startDate, LocalDate endDate) {

    public SyncWindow {
        if (dataset == null || dataset.isBlank()) {
            throw new IllegalArgumentException("数据集不能为空");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("同步窗口日期范围无效");
        }
    }
}
// AI_GENERATE_END----
