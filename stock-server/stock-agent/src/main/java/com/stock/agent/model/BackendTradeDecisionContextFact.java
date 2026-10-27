// AI_GENERATE_START ---
package com.stock.agent.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Backend 固化的不可变交易决策上下文事实。
 *
 * @param contextId 上下文唯一标识
 * @param contextHash 上下文 SHA-256
 * @param candidateBatchId 候选批次标识
 * @param predictionBatchId 预测批次标识
 * @param stockCode 股票代码
 * @param stockName 股票名称
 * @param modelVersion 模型版本
 * @param sentimentModelVersion 情感模型版本
 * @param strategyVersion 策略版本
 * @param baseTradeDate 基准交易日
 * @param targetTradeDate 目标交易日
 * @param informationCutoffAt 信息截止时间
 * @param baselineRank 基线排名
 * @param baselineScore 基线综合分数
 * @param lstmScore LSTM 分数
 * @param predictedReturn 预测收益率
 * @param directionProbability 上涨概率
 * @param downsideRisk 下行风险
 * @param newsImpactScore 新闻影响分数
 * @param candidateReason 入选理由
 * @param baselineEligible 基线是否允许
 * @param closePrice 最近收盘价，单位元
 * @param priceTradeDate 价格交易日
 * @param minimumScore 策略最低分数
 * @param accountSnapshotAvailable 账户快照是否可用
 * @param totalAssets 总资产，单位元
 * @param availableCash 可用资金，单位元
 * @param accountCapturedAt 账户快照时间
 * @param dataQualityFlags 数据质量标记
 * @param recentNews 新闻摘要
 * @param evidenceRefs 证据编号
 * @param asOf 上下文固化时间
 * @param expiresAt 上下文失效时间
 * @author mwangli
 * @since 2026-10-23
 */
public record BackendTradeDecisionContextFact(
        String contextId, String contextHash, String candidateBatchId, String predictionBatchId,
        String stockCode, String stockName, String modelVersion, String sentimentModelVersion,
        String strategyVersion, LocalDate baseTradeDate, LocalDate targetTradeDate,
        LocalDateTime informationCutoffAt, int baselineRank, double baselineScore, double lstmScore,
        Double predictedReturn, Double directionProbability, Double downsideRisk,
        double newsImpactScore, String candidateReason, boolean baselineEligible,
        BigDecimal closePrice, LocalDate priceTradeDate, double minimumScore,
        boolean accountSnapshotAvailable, BigDecimal totalAssets, BigDecimal availableCash,
        LocalDateTime accountCapturedAt, List<String> dataQualityFlags,
        List<BackendTradeDecisionNewsFact> recentNews, List<String> evidenceRefs,
        LocalDateTime asOf, LocalDateTime expiresAt) {
}
// AI_GENERATE_END --