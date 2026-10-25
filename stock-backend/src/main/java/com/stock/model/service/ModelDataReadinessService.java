// AI_GENERATE_START -
package com.stock.model.service;

import com.stock.market.dto.ModelTrainingDataAuditDto;
import com.stock.market.service.MarketDataQueryService;
import com.stock.model.config.LstmDataQualityConfig;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.ModelDataReadinessReport;
import com.stock.model.dto.NewsFeatureSnapshotAuditResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型训练数据就绪检查服务。
 * 在提取不可变快照前统一审计模型基础集合和新闻日级特征快照。
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

    /**
     * 检查指定股票范围是否具备训练快照构建条件。
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
        List<String> externalGaps = foundationGaps(foundationAudit, minimumCoverageRate);
        boolean foundationReady = !codes.isEmpty() && externalGaps.isEmpty();
        NewsFeatureSnapshotAuditResult newsAudit = newsSnapshotBackfillService.audit();
        boolean newsReady = newsAudit.passed();
        boolean automaticBackfillAvailable = foundationReady && !newsReady
                && newsAudit.expectedCount() > 0L;
        List<String> automaticGaps = automaticBackfillAvailable
                ? List.of("news_feature_snapshots") : List.of();
        boolean ready = foundationReady && newsReady;
        String message = ready
                ? "模型训练数据质量检查通过"
                : buildWaitingMessage(externalGaps, automaticGaps, newsAudit);
        return ModelDataReadinessReport.builder()
                .ready(ready)
                .foundationReady(foundationReady)
                .newsSnapshotsReady(newsReady)
                .automaticBackfillAvailable(automaticBackfillAvailable)
                .minimumSourceCoverageRate(minimumCoverageRate)
                .foundationAudit(foundationAudit)
                .newsSnapshotAudit(newsAudit)
                .automaticBackfillGaps(automaticGaps)
                .externalDataGaps(externalGaps)
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
        addCoverageGap(gaps, "model_stock_daily_bars",
                audit.getModelBarStockCount(), requested, minimumCoverageRate,
                audit.getMissingModelBarCodes());
        addCoverageGap(gaps, "stock_adjustment_factors",
                audit.getAdjustmentFactorStockCount(), requested, minimumCoverageRate,
                audit.getMissingAdjustmentFactorCodes());
        addCoverageGap(gaps, "historical_stock_universe",
                audit.getHistoricalUniverseStockCount(), requested, minimumCoverageRate,
                audit.getMissingHistoricalUniverseCodes());
        addCoverageGap(gaps, "stock_daily_tradability_facts",
                audit.getTradabilityStockCount(), requested, minimumCoverageRate,
                audit.getMissingTradabilityCodes());
        if (!audit.isBenchmarkSeriesAvailable()) {
            gaps.add("model_reference_daily_bars:缺少市场基准序列");
        }
        if (audit.getRequiredIndustrySeriesCount() <= 0) {
            gaps.add("historical_stock_universe:缺少有效行业代码");
        } else {
            addCoverageGap(gaps, "model_reference_daily_bars:INDUSTRY",
                    audit.getAvailableIndustrySeriesCount(), audit.getRequiredIndustrySeriesCount(),
                    minimumCoverageRate, audit.getMissingIndustrySeriesCodes());
        }
        if (audit.getLatestModelBarDate() == null) {
            gaps.add("model_stock_daily_bars:缺少最新交易日");
        }
        return List.copyOf(gaps);
    }

    private void addCoverageGap(
            List<String> gaps,
            String sourceName,
            int covered,
            int required,
            double minimumCoverageRate,
            List<String> missingSamples) {
        double coverageRate = required <= 0 ? 0D : covered / (double) required;
        if (coverageRate < minimumCoverageRate) {
            gaps.add(sourceName + ":覆盖率=" + coverageRate
                    + ",要求=" + minimumCoverageRate + ",缺失样例=" + missingSamples);
        }
    }

    private String buildWaitingMessage(
            List<String> externalGaps,
            List<String> automaticGaps,
            NewsFeatureSnapshotAuditResult newsAudit) {
        if (!externalGaps.isEmpty()) {
            return "模型基础数据未达标，等待外部导入: " + String.join("; ", externalGaps);
        }
        if (!automaticGaps.isEmpty()) {
            return "新闻日级特征快照未达标，可执行一次自动回填: coverage="
                    + newsAudit.coverageRate();
        }
        return "训练数据未达标且当前没有可执行的自动回填动作";
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }
}
// AI_GENERATE_END -
