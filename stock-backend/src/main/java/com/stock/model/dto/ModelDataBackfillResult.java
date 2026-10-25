// AI_GENERATE_START -
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型训练前置数据回填结果。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDataBackfillResult {

    /** 本次是否实际执行了自动回填。 */
    private boolean executed;

    /** 新闻日级特征快照回填结果。 */
    private NewsFeatureSnapshotBackfillResult newsSnapshotBackfill;

    /** 回填结果说明。 */
    private String message;
}
// AI_GENERATE_END -
