// AI_GENERATE_START-----
package com.stock.platform.service;

import com.alibaba.fastjson2.JSON;
import com.stock.platform.dto.InternalTradeDecisionContextResponse;
import com.stock.platform.dto.InternalTradeDecisionNewsResponse;
import com.stock.data.dto.MarketNewsDto;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.data.service.MarketDataQueryService;
import com.stock.model.dto.PredictionResultResponse;
import com.stock.model.service.ModelPredictionQueryService;
import com.stock.strategy.config.StrategyConfigManager;
import com.stock.strategy.algorithm.StockSelector;
import com.stock.strategy.dto.StockRankingDto;
import com.stock.strategy.dto.StrategyConfigDto;
import com.stock.trading.dto.BrokerAccountDto;
import com.stock.trading.service.BrokerReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 在 Backend 权威事实边界内构建不可变交易决策上下文。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTradeDecisionContextService {

    private static final String CANDIDATE_STATUS_ELIGIBLE = "ELIGIBLE";
    private static final int MAX_CANDIDATES = 20;
    private static final int NEWS_LOOKBACK_HOURS = 72;
    private static final LocalTime DECISION_VALID_UNTIL = LocalTime.of(10, 0);

    private final StockSelector stockSelector;
    private final MarketDataQueryService marketDataQueryService;
    private final ModelPredictionQueryService modelPredictionQueryService;
    private final StrategyConfigManager strategyConfigManager;
    private final BrokerReadService brokerReadService;

    /**
     * 为候选批次构建只读决策上下文。
     *
     * @param candidateBatchId 候选批次标识
     * @param maxCandidates 最大候选数量，范围 1 至 20
     * @return 按候选排名排列的上下文
     */
    public List<InternalTradeDecisionContextResponse> buildBatch(
            String candidateBatchId, int maxCandidates) {
        if (!StringUtils.hasText(candidateBatchId)) {
            throw new IllegalArgumentException("候选批次标识不能为空");
        }
        if (maxCandidates < 1 || maxCandidates > MAX_CANDIDATES) {
            throw new IllegalArgumentException("单次交易建议候选数量必须在 1 至 20 之间");
        }
        List<StockRankingDto> rankings = stockSelector
                .getRankingsByCandidateBatchId(candidateBatchId.trim());
        if (rankings.isEmpty()) {
            throw new IllegalArgumentException("候选批次不存在或没有排名数据");
        }
        validateBatchMetadata(rankings);
        StockRankingDto first = rankings.get(0);
        StrategyConfigDto strategyConfig = strategyConfigManager
                .getConfigDtoByVersion(first.getStrategyConfigVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "候选批次引用的策略版本不存在: " + first.getStrategyConfigVersion()));
        List<String> sharedQualityFlags = new ArrayList<>();
        BrokerAccountDto account = loadAccount(first.getInformationCutoffAt(), sharedQualityFlags);
        LocalDateTime expiresAt = first.getTargetTradeDate().atTime(DECISION_VALID_UNTIL);
        return rankings.stream().limit(maxCandidates)
                .map(ranking -> buildContext(ranking, strategyConfig, account,
                        sharedQualityFlags, first.getCalculateTime(), expiresAt))
                .toList();
    }

    private void validateBatchMetadata(List<StockRankingDto> rankings) {
        StockRankingDto baseline = rankings.get(0);
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

    private boolean hasRequiredMetadata(StockRankingDto ranking) {
        return StringUtils.hasText(ranking.getPredictionBatchId())
                && StringUtils.hasText(ranking.getModelVersion())
                && StringUtils.hasText(ranking.getSentimentModelVersion())
                && StringUtils.hasText(ranking.getStrategyConfigVersion())
                && ranking.getBaseTradeDate() != null
                && ranking.getTargetTradeDate() != null
                && ranking.getInformationCutoffAt() != null
                && ranking.getCalculateTime() != null;
    }

    private BrokerAccountDto loadAccount(LocalDateTime cutoffAt, List<String> qualityFlags) {
        try {
            return brokerReadService.getAccountAtOrBefore(cutoffAt);
        } catch (RuntimeException exception) {
            qualityFlags.add("ACCOUNT_SNAPSHOT_UNAVAILABLE");
            log.warn("构建交易决策上下文时账户快照不可用: causeType={}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private InternalTradeDecisionContextResponse buildContext(
            StockRankingDto ranking, StrategyConfigDto strategyConfig, BrokerAccountDto account,
            List<String> sharedQualityFlags, LocalDateTime asOf, LocalDateTime expiresAt) {
        List<String> qualityFlags = new ArrayList<>(sharedQualityFlags);
        StockPriceItemDto price = loadCutoffPrice(ranking, qualityFlags);
        PredictionResultResponse prediction = loadPrediction(ranking, qualityFlags);
        List<InternalTradeDecisionNewsResponse> recentNews = loadRecentNews(ranking, qualityFlags);
        List<String> evidenceRefs = buildEvidenceRefs(
                ranking, price, prediction, recentNews, strategyConfig, account);
        String contextHash = hashCanonical(ranking, price, prediction, recentNews,
                strategyConfig, account, qualityFlags, evidenceRefs);
        String contextId = UUID.nameUUIDFromBytes(contextHash.getBytes(StandardCharsets.UTF_8)).toString();
        return new InternalTradeDecisionContextResponse(
                contextId, contextHash, ranking.getCandidateBatchId(), ranking.getPredictionBatchId(),
                ranking.getStockCode(), ranking.getStockName(), ranking.getModelVersion(),
                ranking.getSentimentModelVersion(), ranking.getStrategyConfigVersion(),
                ranking.getBaseTradeDate(), ranking.getTargetTradeDate(), ranking.getInformationCutoffAt(),
                ranking.getRank(), ranking.getTotalScore(), ranking.getLstmScore(),
                prediction == null ? null : prediction.getPredictedReturn(),
                prediction == null ? null : prediction.getDirectionProbability(),
                prediction == null ? null : prediction.getDownsideRisk(),
                ranking.getNewsImpactScore(), ranking.getReason(),
                CANDIDATE_STATUS_ELIGIBLE.equals(ranking.getStatus()),
                price == null ? null : price.getClosePrice(), price == null ? null : price.getDate(),
                strategyConfig.getMinScore(), account != null,
                account == null ? null : account.getTotalAssets(),
                account == null ? null : account.getAvailableCash(),
                account == null ? null : account.getCapturedAt(),
                List.copyOf(qualityFlags), List.copyOf(recentNews), List.copyOf(evidenceRefs),
                asOf, expiresAt);
    }

    private StockPriceItemDto loadCutoffPrice(StockRankingDto ranking, List<String> qualityFlags) {
        LocalDate cutoffDate = ranking.getInformationCutoffAt().toLocalDate();
        if (ranking.getBaseTradeDate().isBefore(cutoffDate)) {
            cutoffDate = ranking.getBaseTradeDate();
        }
        List<StockPriceItemDto> prices = marketDataQueryService.findRecentPrices(
                ranking.getStockCode(), cutoffDate, 1);
        if (prices.isEmpty()) {
            qualityFlags.add("CUTOFF_PRICE_UNAVAILABLE");
            return null;
        }
        return prices.get(0);
    }

    private PredictionResultResponse loadPrediction(StockRankingDto ranking, List<String> qualityFlags) {
        try {
            PredictionResultResponse prediction = modelPredictionQueryService.getPrediction(
                    ranking.getPredictionBatchId(), ranking.getStockCode());
            if (prediction == null
                    || !Objects.equals(prediction.getBaseTradeDate(), ranking.getBaseTradeDate())
                    || !Objects.equals(prediction.getTargetTradeDate(), ranking.getTargetTradeDate())) {
                qualityFlags.add("PREDICTION_DETAIL_UNAVAILABLE");
                return null;
            }
            return prediction;
        } catch (RuntimeException exception) {
            qualityFlags.add("PREDICTION_DETAIL_UNAVAILABLE");
            log.warn("构建交易决策上下文时预测明细不可用: stockCode={}, causeType={}",
                    ranking.getStockCode(), exception.getClass().getSimpleName());
            return null;
        }
    }

    private List<InternalTradeDecisionNewsResponse> loadRecentNews(
            StockRankingDto ranking, List<String> qualityFlags) {
        LocalDateTime cutoff = ranking.getInformationCutoffAt();
        try {
            return marketDataQueryService.findRecentNews(
                            ranking.getStockCode(), cutoff.minusHours(NEWS_LOOKBACK_HOURS), cutoff).stream()
                    .map(news -> new InternalTradeDecisionNewsResponse(
                            newsEvidenceRef(ranking.getStockCode(), news), limit(news.getTitle(), 200),
                            limit(news.getSource(), 100), limit(news.getCategory(), 32),
                            limit(news.getEventType(), 64), news.getPublishTime()))
                    .toList();
        } catch (RuntimeException exception) {
            qualityFlags.add("RECENT_NEWS_UNAVAILABLE");
            log.warn("构建交易决策上下文时近期新闻不可用: stockCode={}, causeType={}",
                    ranking.getStockCode(), exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<String> buildEvidenceRefs(
            StockRankingDto ranking, StockPriceItemDto price, PredictionResultResponse prediction,
            List<InternalTradeDecisionNewsResponse> recentNews, StrategyConfigDto strategyConfig,
            BrokerAccountDto account) {
        List<String> refs = new ArrayList<>();
        refs.add("candidate:" + ranking.getCandidateBatchId() + ":" + ranking.getStockCode());
        if (price != null) {
            refs.add("price:" + ranking.getStockCode() + ":" + price.getDate());
        }
        if (prediction != null) {
            refs.add("prediction:" + prediction.getBatchId() + ":" + prediction.getStockCode());
        }
        refs.addAll(recentNews.stream().map(InternalTradeDecisionNewsResponse::evidenceRef).toList());
        refs.add("strategy:" + strategyConfig.getVersion());
        if (account != null && account.getCapturedAt() != null) {
            refs.add("account:" + account.getCapturedAt());
        }
        return refs;
    }

    private String hashCanonical(
            StockRankingDto ranking, StockPriceItemDto price, PredictionResultResponse prediction,
            List<InternalTradeDecisionNewsResponse> recentNews, StrategyConfigDto strategyConfig,
            BrokerAccountDto account, List<String> qualityFlags, List<String> evidenceRefs) {
        String canonical = String.join("|",
                value(ranking.getCandidateBatchId()), value(ranking.getPredictionBatchId()),
                value(ranking.getStockCode()), value(ranking.getStockName()), value(ranking.getModelVersion()),
                value(ranking.getSentimentModelVersion()), value(ranking.getStrategyConfigVersion()),
                value(ranking.getBaseTradeDate()), value(ranking.getTargetTradeDate()),
                value(ranking.getInformationCutoffAt()), value(ranking.getRank()),
                value(ranking.getTotalScore()), value(ranking.getLstmScore()),
                value(ranking.getNewsImpactScore()), value(ranking.getStatus()), value(ranking.getReason()),
                value(price == null ? null : price.getDate()),
                value(price == null ? null : price.getClosePrice()),
                value(prediction == null ? null : prediction.getPredictedReturn()),
                value(prediction == null ? null : prediction.getDirectionProbability()),
                value(prediction == null ? null : prediction.getDownsideRisk()), JSON.toJSONString(recentNews),
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

    private String newsEvidenceRef(String stockCode, MarketNewsDto news) {
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

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
// AI_GENERATE_END-----
