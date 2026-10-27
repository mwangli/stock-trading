// AI_GENERATE_START- --
package com.stock.model.service;

import com.stock.data.dto.ModelTrainingDataAuditDto;
import com.stock.data.service.MarketDataQueryService;
import com.stock.data.service.TushareFreeDailySyncService;
import com.stock.model.config.LstmDataQualityConfig;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.ModelDataReadinessReport;
import com.stock.model.dto.NewsFeatureSnapshotAuditResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * V6 模型训练数据就绪检查服务。
 * 免费数据契约只把版本化日线和新闻快照作为硬门禁，其余市场特征由预处理阶段确定性构造。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Service
@RequiredArgsConstructor
public class ModelDataReadinessService {

    private final LstmTrainingConfig trainingConfig;
    private final LstmDataQualityConfig dataQualityConfig;
    private final MarketDataQueryService marketDataQueryService;
    private final NewsFeatureSnapshotBackfillService newsSnapshotBackfillService;
    private final TushareFreeDailySyncService tushareFreeDailySyncService;

    /**
     * 检查指定股票范围是否具备 V6 训练快照构建条件。
     *
     * @param stockCodes 本次训练股票代码
     * @return 数据就绪检查报告
     */
    public ModelDataReadinessReport inspect(List<String> stockCodes) {
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        double minimumCoverageRate = dataQualityConfig.getMinimumSourceCoverageRate();
        if (!Double.isFinite(minimumCoverageRate)
                || minimumCoverageRate <= 0D || minimumCoverageRate > 1D) {
            throw new IllegalStateException("关键数据源最低覆盖率配置必须在 0 到 1 之间");
        }
        ModelTrainingDataAuditDto foundationAudit = marketDataQueryService.auditModelTrainingData(
                codes, requireText(trainingConfig.getDataSource(), "模型数据来源不能为空"),
                requireText(trainingConfig.getDataSourceVersion(), "模型数据版本不能为空"),
                requireText(trainingConfig.getBenchmarkSeriesCode(), "市场基准序列代码不能为空"));
        List<String> foundationGaps = foundationGaps(foundationAudit, minimumCoverageRate);
        boolean foundationReady = !codes.isEmpty() && foundationGaps.isEmpty();
        NewsFeatureSnapshotAuditResult newsAudit = newsSnapshotBackfillService.audit();
        boolean newsReady = foundationReady && newsAudit.passed();

        List<String> automaticGaps = new ArrayList<>();
        List<String> externalGaps = new ArrayList<>();
        if (!foundationGaps.isEmpty()) {
            if (tushareFreeDailySyncService.isConfigured()) {
                automaticGaps.add("model_stock_daily_bars");
            } else {
                externalGaps.addAll(foundationGaps);
            }
        }
        if (foundationReady && !newsReady && newsAudit.expectedCount() > 0L) {
            automaticGaps.add("news_feature_snapshots");
        }
        boolean automaticBackfillAvailable = !automaticGaps.isEmpty();
        boolean ready = foundationReady && newsReady;
        String message = ready
                ? "V6 模型训练数据质量检查通过"
                : buildWaitingMessage(externalGaps, automaticGaps, newsAudit);
        return ModelDataReadinessReport.builder()
                .ready(ready)
                .foundationReady(foundationReady)
                .newsSnapshotsReady(newsReady)
                .automaticBackfillAvailable(automaticBackfillAvailable)
                .minimumSourceCoverageRate(minimumCoverageRate)
                .foundationAudit(foundationAudit)
                .newsSnapshotAudit(newsAudit)
                .automaticBackfillGaps(List.copyOf(automaticGaps))
                .externalDataGaps(List.copyOf(externalGaps))
                .message(message)
                .build();
    }

    private List<String> foundationGaps(
            ModelTrainingDataAuditDto audit, double minimumCoverageRate) {
        List<String> gaps = new ArrayList<>();
        int requested = audit.getRequestedStockCount();
        if (requested <= 0) {
            gaps.add("stock_info:没有可训练股票");
            return gaps;
        }
        double coverageRate = audit.getModelBarStockCount() / (double) requested;
        if (coverageRate < minimumCoverageRate) {
            gaps.add("model_stock_daily_bars:覆盖率=" + coverageRate
                    + ",要求=" + minimumCoverageRate
                    + ",缺失样例=" + audit.getMissingModelBarCodes());
        }
        if (audit.getLatestModelBarDate() == null) {
            gaps.add("model_stock_daily_bars:缺少最新交易日");
        }
        return List.copyOf(gaps);
    }

    private String buildWaitingMessage(
            List<String> externalGaps,
            List<String> automaticGaps,
            NewsFeatureSnapshotAuditResult newsAudit) {
        if (!externalGaps.isEmpty()) {
            return "V6 基础日线未达标且 Tushare 免费接口不可用: " + String.join("; ", externalGaps);
        }
        if (!automaticGaps.isEmpty()) {
            return "V6 训练数据可自动准备: gaps=" + automaticGaps
                    + ", newsCoverage=" + newsAudit.coverageRate();
        }
        return "V6 训练数据未达标且当前没有可执行的自动回填动作";
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }
}
// AI_GENERATE_END- --