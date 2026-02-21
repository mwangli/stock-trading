package online.mwang.stockTrading.results;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LSTM预测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResult {

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 上涨概率 (0-1)
     */
    private double upProbability;

    /**
     * 下跌概率 (0-1)
     */
    private double downProbability;

    /**
     * 预测信号：BUY / SELL / HOLD
     */
    private String signal;

    /**
     * 置信度 (0-1)
     */
    private double confidence;

    /**
     * 预测涨跌幅
     */
    private double predictedChange;

    public static PredictionResult buy(String stockCode, double probability, double confidence) {
        return PredictionResult.builder()
                .stockCode(stockCode)
                .upProbability(probability)
                .downProbability(1 - probability)
                .signal("BUY")
                .confidence(confidence)
                .build();
    }

    public static PredictionResult sell(String stockCode, double probability, double confidence) {
        return PredictionResult.builder()
                .stockCode(stockCode)
                .upProbability(probability)
                .downProbability(1 - probability)
                .signal("SELL")
                .confidence(confidence)
                .build();
    }
}
