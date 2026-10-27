// AI_GENERATE_START- --
package com.stock.model.dto;

import com.stock.data.dto.TushareFreeDailyBackfillResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * V6 模型训练前置数据自动准备结果。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDataBackfillResult {

    /** 本次是否实际执行了自动准备动作。 */
    private boolean executed;

    /** 本轮实际执行的动作名称。 */
    private List<String> executedActions;

    /** Tushare 免费模型日线回填结果，可为空。 */
    private TushareFreeDailyBackfillResult marketDailyBackfill;

    /** 新闻日级特征快照回填结果，可为空。 */
    private NewsFeatureSnapshotBackfillResult newsSnapshotBackfill;

    /** 回填结果说明。 */
    private String message;
}
// AI_GENERATE_END- --