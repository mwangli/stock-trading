// AI_GENERATE_START---------
package com.stock.agent.decision.service;

import com.alibaba.fastjson2.JSON;
import com.stock.data.entity.StockNews;
import com.stock.data.entity.StockPrice;
import com.stock.data.repository.NewsRepository;
import com.stock.data.repository.PriceRepository;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.repository.ModelPredictionRepository;
import com.stock.strategy.config.StrategyConfigManager;
import com.stock.agent.decision.entity.TradeDecisionContextEntity;
import com.stock.agent.decision.repository.TradeDecisionContextRepository;
import com.stock.agent.decision.vo.TradeDecisionContext;
import com.stock.agent.decision.vo.TradeDecisionNewsSnapshot;
import com.stock.strategy.entity.StockRanking;
import com.stock.strategy.entity.StrategyConfig;
import com.stock.strategy.repository.RankingRepository;
import com.stock.trading.dto.BrokerAccountDto;
import com.stock.trading.service.BrokerReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 交易决策上下文构建服务。
 * 按候选批次固化截止时点可见行情、策略和账户风险摘要，禁止模型自行查询或修改上下文。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeDecisionContextService {

    private static final String CANDIDATE_STATUS_ELIGIBLE = "ELIGIBLE";
    private static final int MAX_CANDIDATES = 20;
    private static final int NEWS_LOOKBACK_HOURS = 72;
    private static final LocalTime DECISION_VALID_UNTIL = LocalTime.of(10, 0);

    private final RankingRepository rankingRepository;
    private final PriceRepository priceRepository;
    private final ModelPredictionRepository predictionRepository;
    private final NewsRepository newsRepository;
    private final StrategyConfigManager strategyConfigService;
    private final BrokerReadService brokerReadService;
    private final TradeDecisionContextRepository contextRepository;

    /**
     * 为候选批次构建并持久化不可变决策上下文。
     *
     * @param candidateBatchId 候选批次标识
     * @param maxCandidates 最大候选数量，范围 1 至 20
     * @return 按候选排名排列的决策上下文
     * @throws IllegalArgumentException 当批次不存在、元数据冲突或数量越界时抛出
     */
    public List<TradeDecisionContext> buildBatch(String candidateBatchId, int maxCandidates) {
        if (!StringUtils.hasText(candidateBatchId)) {
            throw new IllegalArgumentException("候选批次标识不能为空");
        }
        if (maxCandidates < 1 || maxCandidates > MAX_CANDIDATES) {
            throw new IllegalArgumentException("单次影子决策候选数量必须在 1 至 20 之间");
        }
        List<StockRanking> rankings = rankingRepository
                .findByCandidateBatchIdOrderByRankAsc(candidateBatchId.trim());
        if (rankings.isEmpty()) {
            throw new IllegalArgumentException("候选批次不存在或没有排名数据");
        }
        validateBatchMetadata(rankings);

        List<String> sharedQualityFlags = new ArrayList<>();
        StrategyConfig strategyConfig = resolveStrategyConfig(rankings.get(0));
        LocalDateTime informationCutoffAt = rankings.get(0).getInformationCutoffAt();
        BrokerAccountDto account = loadAccount(informationCutoffAt, sharedQualityFlags);
        LocalDateTime asOf = rankings.get(0).getCalculateTime();
        LocalDateTime expiresAt = rankings.get(0).getTargetTradeDate().atTime(DECISION_VALID_UNTIL);

        return rankings.stream()
                .limit(maxCandidates)
                .map(ranking -> buildContext(
                        ranking, strategyConfig, account,
                        sharedQualityFlags, asOf, expiresAt))
                .toList();
    }

    private void validateBatchMetadata(List<StockRanking> rankings) {
        StockRanking baseline = rankings.get(0);
        boolean inconsistent = rankings.stream().anyMatch(item ->
                !Objects.equals(baseline.getCandidateBatchId(), item.getCandidateBatchId())
                        || !Objects.equals(baseline.getPredictionBatchId(), item.getPredictionBatchId())
                        || !Objects.equals(baseline.getModelVersion(), item.getModelVersion())
                        || !Objects.equals(baseline.getSentimentModelVersion(), item.getSentimentModelVersion())
                        || !Objects.equals(baseline.getStrategyConfigVersion(), item.getStrategyConfigVersion())
                        || !Objects.equals(baseline.getBaseTradeDate(), item.getBaseTradeDate())
                        || !Objects.equals(baseline.getTargetTradeDate(), item.getTargetTradeDate())
                        || !Objects.equals(baseline.getInformationCutoffAt(), item.getInformationCutoffAt())
                        || !Objects.equals(baseline.getCalculateTime(), item.getCalculateTime()));
        if (inconsistent || !hasRequiredMetadata(baseline)) {
            throw new IllegalArgumentException("候选批次追溯元数据缺失或批次内不一致");
        }
    }

    private boolean hasRequiredMetadata(StockRanking ranking) {
        return StringUtils.hasText(ranking.getPredictionBatchId())
                && StringUtils.hasText(ranking.getModelVersion())
                && StringUtils.hasText(ranking.getSentimentModelVersion())
                && StringUtils.hasText(ranking.getStrategyConfigVersion())
                && ranking.getBaseTradeDate() != null
                && ranking.getTargetTradeDate() != null
                && ranking.getInformationCutoffAt() != null
                && ranking.getCalculateTime() != null;
    }

    private StrategyConfig resolveStrategyConfig(StockRanking ranking) {
        return strategyConfigService.getConfigByVersion(ranking.getStrategyConfigVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "候选批次引用的策略版本不存在: " + ranking.getStrategyConfigVersion()));
    }

    private BrokerAccountDto loadAccount(LocalDateTime informationCutoffAt, List<String> qualityFlags) {
        try {
            return brokerReadService.getAccountAtOrBefore(informationCutoffAt);
        } catch (RuntimeException exception) {
            qualityFlags.add("ACCOUNT_SNAPSHOT_UNAVAILABLE");
            log.warn("构建交易决策上下文时账户快照不可用: causeType={}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private TradeDecisionContext buildContext(StockRanking ranking,
                                                StrategyConfig strategyConfig,
                                                BrokerAccountDto account,
                                                List<String> sharedQualityFlags,
                                                LocalDateTime asOf,
                                                LocalDateTime expiresAt) {
        List<String> qualityFlags = new ArrayList<>(sharedQualityFlags);
        StockPrice price = loadCutoffPrice(ranking, qualityFlags);
        ModelPredictionDocument prediction = loadPrediction(ranking, qualityFlags);
        List<TradeDecisionNewsSnapshot> recentNews = loadRecentNews(ranking, qualityFlags);
        List<String> evidenceRefs = buildEvidenceRefs(
                ranking, price, prediction, recentNews, strategyConfig, account);
        String contextHash = hashCanonical(ranking, price, prediction, recentNews,
                strategyConfig, account, qualityFlags, evidenceRefs);
        String contextId = contextRepository.findByContextHash(contextHash)
                .map(TradeDecisionContextEntity::getContextId)
                .orElseGet(() -> UUID.randomUUID().toString());
        TradeDecisionContext context = TradeDecisionContext.builder()
                .contextId(contextId)
                .contextHash(contextHash)
                .candidateBatchId(ranking.getCandidateBatchId())
                .predictionBatchId(ranking.getPredictionBatchId())
                .stockCode(ranking.getStockCode())
                .stockName(ranking.getStockName())
                .modelVersion(ranking.getModelVersion())
                .sentimentModelVersion(ranking.getSentimentModelVersion())
                .strategyVersion(ranking.getStrategyConfigVersion())
                .baseTradeDate(ranking.getBaseTradeDate())
                .targetTradeDate(ranking.getTargetTradeDate())
                .informationCutoffAt(ranking.getInformationCutoffAt())
                .baselineRank(ranking.getRank())
                .baselineScore(ranking.getTotalScore())
                .lstmScore(ranking.getLstmScore())
                .predictedReturn(prediction == null ? null : prediction.getPredictedReturn())
                .directionProbability(prediction == null ? null : prediction.getDirectionProbability())
                .downsideRisk(prediction == null ? null : prediction.getDownsideRisk())
                .newsImpactScore(ranking.getNewsImpactScore())
                .candidateReason(ranking.getReason())
                .baselineEligible(CANDIDATE_STATUS_ELIGIBLE.equals(ranking.getStatus()))
                .closePrice(price == null ? null : price.getClosePrice())
                .priceTradeDate(price == null ? null : price.getDate())
                .minimumScore(strategyConfig.getMinScore())
                .accountSnapshotAvailable(account != null)
                .totalAssets(account == null ? null : account.getTotalAssets())
                .availableCash(account == null ? null : account.getAvailableCash())
                .accountCapturedAt(account == null ? null : account.getCapturedAt())
                .dataQualityFlags(List.copyOf(qualityFlags))
                .recentNews(List.copyOf(recentNews))
                .evidenceRefs(List.copyOf(evidenceRefs))
                .asOf(asOf)
                .expiresAt(expiresAt)
                .build();
        persistContext(context);
        return context;
    }

    private StockPrice loadCutoffPrice(StockRanking ranking, List<String> qualityFlags) {
        LocalDate cutoffDate = ranking.getInformationCutoffAt().toLocalDate();
        if (ranking.getBaseTradeDate().isBefore(cutoffDate)) {
            cutoffDate = ranking.getBaseTradeDate();
        }
        List<StockPrice> prices = priceRepository.findByCodeAndDateLessThanEqualOrderByDateDesc(
                ranking.getStockCode(), cutoffDate, PageRequest.of(0, 1));
        if (prices.isEmpty()) {
            qualityFlags.add("CUTOFF_PRICE_UNAVAILABLE");
            return null;
        }
        return prices.get(0);
    }

    private ModelPredictionDocument loadPrediction(StockRanking ranking, List<String> qualityFlags) {
        try {
            return predictionRepository.findByBatchIdAndStockCode(
                            ranking.getPredictionBatchId(), ranking.getStockCode())
                    .filter(prediction -> Objects.equals(
                            prediction.getBaseTradeDate(), ranking.getBaseTradeDate()))
                    .filter(prediction -> Objects.equals(
                            prediction.getTargetTradeDate(), ranking.getTargetTradeDate()))
                    .orElseGet(() -> {
                        qualityFlags.add("PREDICTION_DETAIL_UNAVAILABLE");
                        return null;
                    });
        } catch (RuntimeException exception) {
            qualityFlags.add("PREDICTION_DETAIL_UNAVAILABLE");
            log.warn("构建交易决策上下文时预测明细不可用: stockCode={}, causeType={}",
                    ranking.getStockCode(), exception.getClass().getSimpleName());
            return null;
        }
    }

    private List<TradeDecisionNewsSnapshot> loadRecentNews(
            StockRanking ranking, List<String> qualityFlags) {
        LocalDateTime cutoff = ranking.getInformationCutoffAt();
        try {
            return newsRepository.findTop5ByStockCodeAndPublishTimeBetweenOrderByPublishTimeDesc(
                            ranking.getStockCode(), cutoff.minusHours(NEWS_LOOKBACK_HOURS), cutoff).stream()
                    .map(news -> TradeDecisionNewsSnapshot.builder()
                            .evidenceRef(newsEvidenceRef(ranking.getStockCode(), news))
                            .title(limit(news.getTitle(), 200))
                            .source(limit(news.getSource(), 100))
                            .category(limit(news.getCategory(), 32))
                            .eventType(limit(news.getEventType(), 64))
                            .publishTime(news.getPublishTime())
                            .build())
                    .toList();
        } catch (RuntimeException exception) {
            qualityFlags.add("RECENT_NEWS_UNAVAILABLE");
            log.warn("构建交易决策上下文时近期新闻不可用: stockCode={}, causeType={}",
                    ranking.getStockCode(), exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<String> buildEvidenceRefs(StockRanking ranking, StockPrice price,
                                            ModelPredictionDocument prediction,
                                            List<TradeDecisionNewsSnapshot> recentNews,
                                            StrategyConfig strategyConfig,
                                            BrokerAccountDto account) {
        List<String> refs = new ArrayList<>();
        refs.add("candidate:" + ranking.getCandidateBatchId() + ":" + ranking.getStockCode());
        if (price != null) {
            refs.add("price:" + ranking.getStockCode() + ":" + price.getDate());
        }
        if (prediction != null) {
            refs.add("prediction:" + prediction.getBatchId() + ":" + prediction.getStockCode());
        }
        refs.addAll(recentNews.stream().map(TradeDecisionNewsSnapshot::getEvidenceRef).toList());
        refs.add("strategy:" + strategyConfig.getVersion());
        if (account != null && account.getCapturedAt() != null) {
            refs.add("account:" + account.getCapturedAt());
        }
        return refs;
    }

    private String hashCanonical(StockRanking ranking, StockPrice price,
                                 ModelPredictionDocument prediction,
                                 List<TradeDecisionNewsSnapshot> recentNews,
                                 StrategyConfig strategyConfig,
                                 BrokerAccountDto account, List<String> qualityFlags,
                                 List<String> evidenceRefs) {
        String canonical = String.join("|",
                value(ranking.getCandidateBatchId()), value(ranking.getPredictionBatchId()),
                value(ranking.getStockCode()), value(ranking.getStockName()),
                value(ranking.getModelVersion()),
                value(ranking.getSentimentModelVersion()), value(ranking.getStrategyConfigVersion()),
                value(ranking.getBaseTradeDate()), value(ranking.getTargetTradeDate()),
                value(ranking.getInformationCutoffAt()), value(ranking.getRank()),
                value(ranking.getTotalScore()), value(ranking.getLstmScore()),
                value(ranking.getNewsImpactScore()), value(ranking.getStatus()),
                value(ranking.getReason()),
                value(price == null ? null : price.getDate()),
                value(price == null ? null : price.getClosePrice()),
                value(prediction == null ? null : prediction.getPredictedReturn()),
                value(prediction == null ? null : prediction.getDirectionProbability()),
                value(prediction == null ? null : prediction.getDownsideRisk()),
                JSON.toJSONString(recentNews),
                value(strategyConfig.getVersion()), value(strategyConfig.getMinScore()),
                value(account == null ? null : account.getTotalAssets()),
                value(account == null ? null : account.getAvailableCash()),
                value(account == null ? null : account.getCapturedAt()),
                String.join(",", qualityFlags), String.join(",", evidenceRefs));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void persistContext(TradeDecisionContext context) {
        if (contextRepository.existsById(context.getContextId())) {
            return;
        }
        contextRepository.save(new TradeDecisionContextEntity()
                .setContextId(context.getContextId())
                .setContextHash(context.getContextHash())
                .setCandidateBatchId(context.getCandidateBatchId())
                .setStockCode(context.getStockCode())
                .setBaseTradeDate(context.getBaseTradeDate())
                .setTargetTradeDate(context.getTargetTradeDate())
                .setBaselineEligible(context.isBaselineEligible())
                .setBaselineClosePrice(context.getClosePrice())
                .setContextJson(JSON.toJSONString(context))
                .setInformationCutoffAt(context.getInformationCutoffAt())
                .setExpiresAt(context.getExpiresAt())
                .setCreatedAt(context.getAsOf()));
    }

    private String newsEvidenceRef(String stockCode, StockNews news) {
        String identity = StringUtils.hasText(news.getContentHash()) ? news.getContentHash()
                : StringUtils.hasText(news.getExternalId()) ? news.getExternalId() : news.getId();
        return "news:" + stockCode + ":" + value(identity);
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
// AI_GENERATE_END---------
