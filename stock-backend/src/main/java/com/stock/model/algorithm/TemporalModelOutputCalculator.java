// AI_GENERATE_START ----
package com.stock.model.algorithm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时序双模型四任务输出融合与最终模型评分计算器。
 *
 * @see <a href="http://localhost:5173/docs/backend/model/designs/temporal-model-ensemble">
 *     双模型连续权重融合详细设计</a>
 * @author mwangli
 * @since 2026-10-24
 */
@Component
public class TemporalModelOutputCalculator {

    private static final double RETURN_SCALE = 20D;

    /**
     * 按同一组双模型权重融合单个交易日的四任务输出，并重算上涨概率百分位和最终模型评分。
     *
     * @param inputs 同一交易日的股票输出
     * @param rankingLstmWeight Ranking LSTM 权重
     * @param masterWeight MASTER 权重
     * @return 按输入顺序返回的有效生产输出
     * @throws IllegalArgumentException 权重、模型输出或股票标识不符合契约时抛出
     */
    public List<EffectiveOutput> calculateDaily(
            List<WeightedOutput> inputs, double rankingLstmWeight, double masterWeight) {
        validateWeights(rankingLstmWeight, masterWeight);
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<PartialOutput> partials = new ArrayList<>(inputs.size());
        for (WeightedOutput input : inputs) {
            if (input == null || input.stockCode() == null || input.stockCode().isBlank()) {
                throw new IllegalArgumentException("时序模型输出缺少股票代码");
            }
            requireOutput(input.rankingLstm(), rankingLstmWeight, "Ranking LSTM", input.stockCode());
            requireOutput(input.master(), masterWeight, "MASTER", input.stockCode());
            double effectiveRank = weighted(input.rankingLstm(), input.master(), rankingLstmWeight,
                    masterWeight, ModelOutput::rankScore);
            double effectiveReturn = weighted(input.rankingLstm(), input.master(), rankingLstmWeight,
                    masterWeight, ModelOutput::expectedExcessReturn);
            double effectiveProbability = weighted(input.rankingLstm(), input.master(), rankingLstmWeight,
                    masterWeight, ModelOutput::upProbability);
            double effectiveDownside = weighted(input.rankingLstm(), input.master(), rankingLstmWeight,
                    masterWeight, ModelOutput::downsideQuantile);
            requireProbability("融合上涨概率", effectiveProbability, input.stockCode());
            requireProbability("融合下行风险", effectiveDownside, input.stockCode());
            partials.add(new PartialOutput(input.stockCode(), effectiveRank, effectiveReturn,
                    effectiveProbability, effectiveDownside));
        }
        Map<String, Double> probabilityPercentiles = percentiles(partials);
        return partials.stream().map(output -> toEffectiveOutput(
                output, probabilityPercentiles.get(output.stockCode()))).toList();
    }

    /**
     * 判断四任务模型输出是否满足融合计算契约。
     *
     * @param output 待校验模型输出
     * @return 四个数值均有效且概率与风险位于零到一时返回 true
     */
    public boolean isValid(ModelOutput output) {
        return output != null
                && probability(output.rankScore())
                && Double.isFinite(output.expectedExcessReturn())
                && probability(output.upProbability())
                && probability(output.downsideQuantile());
    }

    private EffectiveOutput toEffectiveOutput(PartialOutput output, double probabilityPercentile) {
        double returnScore = 0.7D * output.rankScore()
                + 0.3D * sigmoid(RETURN_SCALE * output.expectedExcessReturn());
        double directionScore = 0.7D * probabilityPercentile + 0.3D * output.upProbability();
        double riskScore = 1D - output.downsideQuantile();
        double modelScore = 0.55D * returnScore + 0.30D * directionScore + 0.15D * riskScore;
        if (!Double.isFinite(modelScore)) {
            throw new IllegalArgumentException("最终模型评分不是有效数值: stockCode=" + output.stockCode());
        }
        return new EffectiveOutput(output.stockCode(), output.rankScore(), output.expectedExcessReturn(),
                output.upProbability(), probabilityPercentile, output.downsideQuantile(), modelScore);
    }

    private Map<String, Double> percentiles(List<PartialOutput> outputs) {
        List<PartialOutput> ordered = outputs.stream()
                .sorted(Comparator.comparingDouble(PartialOutput::upProbability)
                        .thenComparing(PartialOutput::stockCode))
                .toList();
        Map<String, Double> values = new LinkedHashMap<>();
        int denominator = Math.max(1, ordered.size() - 1);
        int position = 0;
        while (position < ordered.size()) {
            int end = position + 1;
            double value = ordered.get(position).upProbability();
            while (end < ordered.size()
                    && Double.compare(ordered.get(end).upProbability(), value) == 0) {
                end++;
            }
            double percentile = ((position + end - 1D) / 2D) / denominator;
            for (int index = position; index < end; index++) {
                values.put(ordered.get(index).stockCode(), percentile);
            }
            position = end;
        }
        return values;
    }

    private double weighted(
            ModelOutput lstm, ModelOutput master, double lstmWeight, double masterWeight,
            java.util.function.ToDoubleFunction<ModelOutput> selector) {
        return (lstmWeight == 0D ? 0D : lstmWeight * selector.applyAsDouble(lstm))
                + (masterWeight == 0D ? 0D : masterWeight * selector.applyAsDouble(master));
    }

    private void validateWeights(double lstmWeight, double masterWeight) {
        if (!probability(lstmWeight) || !probability(masterWeight)
                || Math.abs(lstmWeight + masterWeight - 1D) > 1.0E-9D) {
            throw new IllegalArgumentException("时序模型权重必须位于 0 到 1 且总和为 1");
        }
    }

    private void requireOutput(ModelOutput output, double weight, String modelName, String stockCode) {
        if (weight > 0D && !isValid(output)) {
            throw new IllegalArgumentException(modelName + " 四任务输出无效: stockCode=" + stockCode);
        }
    }

    private void requireProbability(String name, double value, String stockCode) {
        if (!probability(value)) {
            throw new IllegalArgumentException(name + "超出范围: stockCode=" + stockCode + ", value=" + value);
        }
    }

    private boolean probability(double value) {
        return Double.isFinite(value) && value >= 0D && value <= 1D;
    }

    private double sigmoid(double value) {
        return 1D / (1D + Math.exp(-value));
    }

    /** 单个模型统一四任务输出。 */
    public record ModelOutput(double rankScore, double expectedExcessReturn,
                              double upProbability, double downsideQuantile) {
    }

    /** 单只股票待融合的双模型输出。 */
    public record WeightedOutput(String stockCode, ModelOutput rankingLstm, ModelOutput master) {
    }

    /** 单只股票融合后的生产有效输出。 */
    public record EffectiveOutput(String stockCode, double rankScore, double expectedExcessReturn,
                                  double upProbability, double upProbabilityPercentile,
                                  double downsideQuantile, double modelScore) {
    }

    private record PartialOutput(String stockCode, double rankScore, double expectedExcessReturn,
                                 double upProbability, double downsideQuantile) {
    }
}
// AI_GENERATE_END ----
