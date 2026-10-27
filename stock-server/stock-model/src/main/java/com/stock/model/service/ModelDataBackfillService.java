// AI_GENERATE_START- --
package com.stock.model.service;

import com.stock.data.dto.TushareFreeDailyBackfillResult;
import com.stock.data.service.TushareFreeDailySyncService;
import com.stock.model.dto.ModelDataBackfillResult;
import com.stock.model.dto.ModelDataReadinessReport;
import com.stock.model.dto.NewsFeatureSnapshotBackfillResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * V6 模型训练前置数据回填服务。
 * 按就绪报告依次准备 Tushare 免费日线和点时新闻特征快照。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Service
@RequiredArgsConstructor
public class ModelDataBackfillService {

    private final TushareFreeDailySyncService tushareFreeDailySyncService;
    private final NewsFeatureSnapshotBackfillService newsSnapshotBackfillService;

    /**
     * 根据就绪检查报告执行一轮定向数据准备。
     *
     * @param readinessReport 回填前的数据就绪报告
     * @param stockCodes 本次训练股票代码
     * @param tradingDays 本次训练需要覆盖的交易日数量
     * @return 本轮回填结果
     */
    public ModelDataBackfillResult backfillOnce(
            ModelDataReadinessReport readinessReport,
            List<String> stockCodes,
            int tradingDays) {
        if (readinessReport == null || !readinessReport.isAutomaticBackfillAvailable()) {
            return ModelDataBackfillResult.builder()
                    .executed(false)
                    .executedActions(List.of())
                    .message("没有可执行的自动数据准备动作")
                    .build();
        }
        List<String> gaps = readinessReport.getAutomaticBackfillGaps() == null
                ? List.of() : readinessReport.getAutomaticBackfillGaps();
        List<String> actions = new ArrayList<>();
        TushareFreeDailyBackfillResult marketResult = null;
        NewsFeatureSnapshotBackfillResult newsResult = null;
        if (gaps.contains("model_stock_daily_bars")) {
            marketResult = tushareFreeDailySyncService.backfill(stockCodes, tradingDays);
            actions.add("TUSHARE_FREE_DAILY");
        }
        if (gaps.contains("news_feature_snapshots") || marketResult != null) {
            newsResult = newsSnapshotBackfillService.backfill();
            actions.add("TEMPORAL_NEWS_SNAPSHOT");
        }
        return ModelDataBackfillResult.builder()
                .executed(!actions.isEmpty())
                .executedActions(List.copyOf(actions))
                .marketDailyBackfill(marketResult)
                .newsSnapshotBackfill(newsResult)
                .message(buildMessage(marketResult, newsResult))
                .build();
    }

    private String buildMessage(
            TushareFreeDailyBackfillResult marketResult,
            NewsFeatureSnapshotBackfillResult newsResult) {
        if (marketResult != null && newsResult != null) {
            return newsResult.completed()
                    ? "Tushare 免费日线和新闻快照准备完成"
                    : "Tushare 免费日线已回填，新闻快照已推进一轮";
        }
        if (marketResult != null) {
            return "Tushare 免费日线准备完成";
        }
        if (newsResult != null) {
            return newsResult.completed() ? "新闻快照准备完成" : "新闻快照已推进一轮";
        }
        return "没有执行数据准备动作";
    }
}
// AI_GENERATE_END- --