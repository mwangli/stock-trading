// AI_GENERATE_START -----
package com.stock.strategyAgent.service;

import com.stock.modelService.domain.entity.ModelPredictionDocument;
import com.stock.modelService.persistence.ModelPredictionRepository;
import com.stock.strategyAgent.domain.dto.StrategyEvaluationDto;
import com.stock.strategyAgent.domain.entity.StockRanking;
import com.stock.strategyAgent.domain.entity.StrategyConfig;
import com.stock.strategyAgent.domain.entity.StrategyEvaluationEntity;
import com.stock.strategyAgent.domain.entity.StrategyMode;
import com.stock.strategyAgent.persistence.RankingRepository;
import com.stock.strategyAgent.persistence.StrategyConfigRepository;
import com.stock.strategyAgent.persistence.StrategyEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 策略模式样本外评估服务。
 * 仅使用已生成候选和已回填实际收益，按时间切分验证窗口并持久化三模式结论。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyEvaluationService {

    private static final int LOOKBACK_DAYS = 365;
    private static final int MINIMUM_BATCH_COUNT = 20;
    private static final int MINIMUM_SAMPLE_COUNT = 100;
    private static final double MINIMUM_MATCH_RATE = 0.95D;
    private static final double VALIDATION_RATIO = 0.30D;

    private final RankingRepository rankingRepository;
    private final ModelPredictionRepository predictionRepository;
    private final StrategyConfigRepository configRepository;
    private final StrategyEvaluationRepository evaluationRepository;

    /**
     * 计算并保存三种策略模式的最新评估。
     *
     * @return 本次评估结果
     */
    @Transactional(rollbackFor = Exception.class)
    public StrategyEvaluationDto evaluateAndPersist() {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(LOOKBACK_DAYS);
        List<StockRanking> rankings = rankingRepository
                .findByTargetTradeDateBetweenOrderByTargetTradeDateAscRankAsc(startDate, endDate);
        Map<String, StrategyMode> modeByVersion = configRepository.findAllByOrderByUpdateTimeDesc().stream()
                .filter(config -> config.getVersion() != null && config.getMode() != null)
                .collect(Collectors.toMap(StrategyConfig::getVersion, StrategyConfig::getMode,
                        (left, right) -> left, LinkedHashMap::new));
        Map<PredictionKey, ModelPredictionDocument> predictionByKey = loadPredictions(rankings);
        Map<StrategyMode, List<CandidateOutcome>> outcomesByMode = new EnumMap<>(StrategyMode.class);
        Map<StrategyMode, Integer> expectedByMode = new EnumMap<>(StrategyMode.class);
        for (StockRanking ranking : rankings) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("策略评估已收到停止请求");
            }
            StrategyMode mode = modeByVersion.get(ranking.getStrategyConfigVersion());
            if (mode == null) {
                continue;
            }
            expectedByMode.merge(mode, 1, Integer::sum);
            ModelPredictionDocument prediction = predictionByKey.get(
                    new PredictionKey(ranking.getPredictionBatchId(), ranking.getStockCode()));
            if (prediction == null || prediction.getActualReturn() == null
                    || !Double.isFinite(prediction.getActualReturn())) {
                continue;
            }
            if (ranking.getCandidateBatchId() == null || ranking.getCandidateBatchId().isBlank()
                    || ranking.getTargetTradeDate() == null) {
                continue;
            }
            outcomesByMode.computeIfAbsent(mode, ignored -> new ArrayList<>()).add(new CandidateOutcome(
                    ranking.getCandidateBatchId(), ranking.getTargetTradeDate(), prediction.getActualReturn()));
        }

        String runId = UUID.randomUUID().toString();
        LocalDateTime calculatedAt = LocalDateTime.now();
        List<StrategyEvaluationEntity> entities = new ArrayList<>();
        for (StrategyMode mode : StrategyMode.values()) {
            entities.add(evaluateMode(runId, calculatedAt, mode,
                    outcomesByMode.getOrDefault(mode, List.of()), expectedByMode.getOrDefault(mode, 0)));
        }
        evaluationRepository.saveAll(entities);
        StrategyEvaluationDto result = toDto(entities);
        log.info("策略模式评估完成: runId={}, rankings={}, matchedPredictions={}, summary={}",
                runId, rankings.size(), outcomesByMode.values().stream().mapToInt(List::size).sum(), result.getSummary());
        return result;
    }

    /**
     * 查询最近一次已持久化评估；尚未运行时返回明确的未评估状态。
     *
     * @return 最近评估结果
     */
    public StrategyEvaluationDto getLatest() {
        return evaluationRepository.findTopByOrderByCalculatedAtDesc()
                .map(latest -> toDto(evaluationRepository
                        .findByEvaluationRunIdOrderByStrategyModeAsc(latest.getEvaluationRunId())))
                .orElseGet(this::notEvaluatedResult);
    }

    private Map<PredictionKey, ModelPredictionDocument> loadPredictions(List<StockRanking> rankings) {
        List<String> batchIds = rankings.stream().map(StockRanking::getPredictionBatchId)
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (batchIds.isEmpty()) {
            return Map.of();
        }
        return predictionRepository.findByBatchIdIn(batchIds).stream()
                .filter(item -> item.getBatchId() != null && item.getStockCode() != null)
                .collect(Collectors.toMap(
                        item -> new PredictionKey(item.getBatchId(), item.getStockCode()),
                        Function.identity(), (left, right) -> left, HashMap::new));
    }

    private StrategyEvaluationEntity evaluateMode(String runId, LocalDateTime calculatedAt,
                                                    StrategyMode mode, List<CandidateOutcome> outcomes,
                                                    int expectedSamples) {
        List<DailyOutcome> dailyOutcomes = outcomes.stream()
                .collect(Collectors.groupingBy(CandidateOutcome::candidateBatchId, LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::toDailyOutcome)))
                .values().stream().sorted(Comparator.comparing(DailyOutcome::targetTradeDate)).toList();
        int validationStartIndex = dailyOutcomes.isEmpty() ? 0
                : Math.max(0, (int) Math.floor(dailyOutcomes.size() * (1D - VALIDATION_RATIO)));
        List<DailyOutcome> validation = dailyOutcomes.subList(validationStartIndex, dailyOutcomes.size());
        int validationSamples = validation.stream().mapToInt(DailyOutcome::sampleCount).sum();
        double matchRate = expectedSamples == 0 ? 0D : outcomes.size() / (double) expectedSamples;
        boolean dataComplete = expectedSamples > 0 && matchRate >= MINIMUM_MATCH_RATE;
        boolean sampleSufficient = dailyOutcomes.size() >= MINIMUM_BATCH_COUNT
                && outcomes.size() >= MINIMUM_SAMPLE_COUNT && validation.size() >= 5;
        boolean available = dataComplete && sampleSufficient;

        Metrics metrics = available ? calculateMetrics(validation) : null;
        String conclusion = buildConclusion(mode, expectedSamples, outcomes.size(), dailyOutcomes.size(),
                validationSamples, dataComplete, sampleSufficient, metrics);
        return StrategyEvaluationEntity.builder()
                .evaluationRunId(runId)
                .strategyMode(mode.name())
                .status(available ? "AVAILABLE" : "INSUFFICIENT_DATA")
                .sourceType("CANDIDATE")
                .windowStart(dailyOutcomes.isEmpty() ? null : dailyOutcomes.get(0).targetTradeDate())
                .windowEnd(dailyOutcomes.isEmpty() ? null : dailyOutcomes.get(dailyOutcomes.size() - 1).targetTradeDate())
                .validationStart(validation.isEmpty() ? null : validation.get(0).targetTradeDate())
                .validationEnd(validation.isEmpty() ? null : validation.get(validation.size() - 1).targetTradeDate())
                .sampleCount(outcomes.size())
                .batchCount(dailyOutcomes.size())
                .grossReturn(metrics == null ? null : metrics.grossReturn())
                .maxDrawdown(metrics == null ? null : metrics.maxDrawdown())
                .winRate(metrics == null ? null : metrics.winRate())
                .profitLossRatio(metrics == null ? null : metrics.profitLossRatio())
                .stabilityScore(metrics == null ? null : metrics.stabilityScore())
                .outOfSample(available)
                .dataComplete(dataComplete)
                .sampleSufficient(sampleSufficient)
                .conclusion(conclusion)
                .calculatedAt(calculatedAt)
                .build();
    }

    private DailyOutcome toDailyOutcome(List<CandidateOutcome> outcomes) {
        double averageReturn = outcomes.stream().mapToDouble(CandidateOutcome::actualReturn).average().orElse(0D);
        return new DailyOutcome(outcomes.get(0).targetTradeDate(), averageReturn, outcomes.size());
    }

    private Metrics calculateMetrics(List<DailyOutcome> outcomes) {
        double equity = 1D;
        double peak = 1D;
        double maxDrawdown = 0D;
        int wins = 0;
        List<Double> positives = new ArrayList<>();
        List<Double> negatives = new ArrayList<>();
        for (DailyOutcome outcome : outcomes) {
            double dailyReturn = Math.max(-0.999D, outcome.averageReturn());
            equity *= 1D + dailyReturn;
            peak = Math.max(peak, equity);
            maxDrawdown = Math.max(maxDrawdown, peak == 0D ? 0D : (peak - equity) / peak);
            if (dailyReturn > 0D) {
                wins++;
                positives.add(dailyReturn);
            } else if (dailyReturn < 0D) {
                negatives.add(dailyReturn);
            }
        }
        double mean = outcomes.stream().mapToDouble(DailyOutcome::averageReturn).average().orElse(0D);
        double variance = outcomes.stream().mapToDouble(item -> Math.pow(item.averageReturn() - mean, 2D))
                .average().orElse(0D);
        double standardDeviation = Math.sqrt(variance);
        double stability = Math.max(0D, Math.min(1D, 1D - standardDeviation / 0.05D));
        double averageProfit = positives.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double averageLoss = Math.abs(negatives.stream().mapToDouble(Double::doubleValue).average().orElse(0D));
        Double ratio = averageLoss == 0D ? null : averageProfit / averageLoss;
        return new Metrics(equity - 1D, maxDrawdown, wins / (double) outcomes.size(), ratio, stability);
    }

    private String buildConclusion(StrategyMode mode, int expectedSamples, int matchedSamples,
                                   int batchCount, int validationSamples, boolean dataComplete,
                                   boolean sampleSufficient, Metrics metrics) {
        if (expectedSamples == 0) {
            return mode.getName() + "没有可追溯历史候选，暂不能比较或参与自动切换";
        }
        if (!dataComplete) {
            return String.format("%s候选与实际结果匹配不足：%d/%d，暂不能参与自动切换",
                    mode.getName(), matchedSamples, expectedSamples);
        }
        if (!sampleSufficient) {
            return String.format("%s仅有%d个批次、%d个候选，未达到%d批次且%d候选的门槛",
                    mode.getName(), batchCount, matchedSamples, MINIMUM_BATCH_COUNT, MINIMUM_SAMPLE_COUNT);
        }
        return String.format("%s样本外窗口包含%d个候选，毛收益率%.2f%%、最大回撤%.2f%%、胜率%.2f%%",
                mode.getName(), validationSamples, metrics.grossReturn() * 100D,
                metrics.maxDrawdown() * 100D, metrics.winRate() * 100D);
    }

    private StrategyEvaluationDto toDto(List<StrategyEvaluationEntity> entities) {
        List<StrategyEvaluationDto.ModeEvaluationDto> modes = entities.stream()
                .sorted(Comparator.comparing(StrategyEvaluationEntity::getStrategyMode))
                .map(this::toModeDto).toList();
        long availableCount = modes.stream().filter(item -> "AVAILABLE".equals(item.getStatus())).count();
        StrategyEvaluationEntity first = entities.isEmpty() ? null : entities.get(0);
        return StrategyEvaluationDto.builder()
                .evaluationRunId(first == null ? null : first.getEvaluationRunId())
                .calculatedAt(first == null ? null : first.getCalculatedAt())
                .modes(modes)
                .summary(availableCount == 0
                        ? "当前没有达到门槛的策略模式，自动切换只能继续使用市场规则"
                        : "已有 " + availableCount + " 个模式形成样本外结论；只有数据完整且样本充分的结果可用于人工决策")
                .build();
    }

    private StrategyEvaluationDto.ModeEvaluationDto toModeDto(StrategyEvaluationEntity entity) {
        StrategyMode mode = StrategyMode.valueOf(entity.getStrategyMode());
        return StrategyEvaluationDto.ModeEvaluationDto.builder()
                .mode(entity.getStrategyMode()).modeName(mode.getName()).status(entity.getStatus())
                .sourceType(entity.getSourceType()).windowStart(entity.getWindowStart()).windowEnd(entity.getWindowEnd())
                .validationStart(entity.getValidationStart()).validationEnd(entity.getValidationEnd())
                .sampleCount(entity.getSampleCount()).batchCount(entity.getBatchCount())
                .grossReturn(entity.getGrossReturn()).maxDrawdown(entity.getMaxDrawdown())
                .winRate(entity.getWinRate()).profitLossRatio(entity.getProfitLossRatio())
                .stabilityScore(entity.getStabilityScore()).outOfSample(entity.isOutOfSample())
                .dataComplete(entity.isDataComplete()).sampleSufficient(entity.isSampleSufficient())
                .conclusion(entity.getConclusion()).build();
    }

    private StrategyEvaluationDto notEvaluatedResult() {
        List<StrategyEvaluationDto.ModeEvaluationDto> modes = java.util.Arrays.stream(StrategyMode.values())
                .map(mode -> StrategyEvaluationDto.ModeEvaluationDto.builder()
                        .mode(mode.name()).modeName(mode.getName()).status("NOT_EVALUATED")
                        .sourceType("CANDIDATE").conclusion("尚未执行策略评估任务")
                        .dataComplete(false).sampleSufficient(false).outOfSample(false).build())
                .toList();
        return StrategyEvaluationDto.builder().modes(modes)
                .summary("尚未执行策略评估任务，可在任务管理页面手动触发").build();
    }

    private record PredictionKey(String batchId, String stockCode) { }

    private record CandidateOutcome(String candidateBatchId, LocalDate targetTradeDate, double actualReturn) { }

    private record DailyOutcome(LocalDate targetTradeDate, double averageReturn, int sampleCount) { }

    private record Metrics(double grossReturn, double maxDrawdown, double winRate,
                           Double profitLossRatio, double stabilityScore) { }
}
// AI_GENERATE_END -----
