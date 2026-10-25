// AI_GENERATE_START -
package com.stock.model.service;

import com.stock.model.dto.ModelDataBackfillResult;
import com.stock.model.dto.ModelDataReadinessReport;
import com.stock.model.dto.NewsFeatureSnapshotBackfillResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模型训练前置数据回填服务。
 * 只执行已有可靠来源支持的幂等回填，不为缺失的市场事实生成推测值。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Service
@RequiredArgsConstructor
public class ModelDataBackfillService {

    private final NewsFeatureSnapshotBackfillService newsSnapshotBackfillService;

    /**
     * 根据就绪检查报告执行至多一轮定向回填。
     *
     * @param readinessReport 回填前的数据就绪报告
     * @return 本轮回填结果
     */
    public ModelDataBackfillResult backfillOnce(ModelDataReadinessReport readinessReport) {
        if (readinessReport == null || !readinessReport.isAutomaticBackfillAvailable()) {
            return ModelDataBackfillResult.builder()
                    .executed(false)
                    .message("没有可执行的自动基础数据回填动作")
                    .build();
        }
        NewsFeatureSnapshotBackfillResult result = newsSnapshotBackfillService.backfill();
        return ModelDataBackfillResult.builder()
                .executed(true)
                .newsSnapshotBackfill(result)
                .message(result.completed()
                        ? "新闻日级特征快照回填完成"
                        : "新闻日级特征快照已执行一轮回填，保留游标等待后续继续")
                .build();
    }
}
// AI_GENERATE_END -
