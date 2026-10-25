// AI_GENERATE_START -
package com.stock.model.dto;

import com.stock.market.dto.ModelTrainingDataAuditDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型训练数据就绪检查报告。
 * 区分可自动回填的新闻快照缺口和必须由外部数据导入修复的基础数据缺口。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDataReadinessReport {

    /** 数据质量检查是否整体通过。 */
    private boolean ready;

    /** 模型基础数据集合是否达到股票标识覆盖门槛。 */
    private boolean foundationReady;

    /** 新闻日级特征快照是否通过覆盖率和点时审计。 */
    private boolean newsSnapshotsReady;

    /** 本次是否允许触发一次自动回填。 */
    private boolean automaticBackfillAvailable;

    /** 最低股票标识覆盖率，范围 0 到 1。 */
    private double minimumSourceCoverageRate;

    /** 市场基础数据审计详情。 */
    private ModelTrainingDataAuditDto foundationAudit;

    /** 新闻日级特征快照审计详情。 */
    private NewsFeatureSnapshotAuditResult newsSnapshotAudit;

    /** 可由现有幂等服务自动回填的数据缺口。 */
    private List<String> automaticBackfillGaps;

    /** 必须通过外部数据导入修复的基础数据缺口。 */
    private List<String> externalDataGaps;

    /** 面向训练运行记录的检查结论。 */
    private String message;
}
// AI_GENERATE_END -
