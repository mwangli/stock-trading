package online.mwang.stockTrading.modules.sentiment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情感分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentResult {

    /**
     * 情感得分：-1(负面) 到 1(正面)
     */
    private double score;

    /**
     * 负面概率
     */
    private double probNegative;

    /**
     * 中性概率
     */
    private double probNeutral;

    /**
     * 正面概率
     */
    private double probPositive;

    /**
     * 标签：positive / neutral / negative
     */
    private String label;

    /**
     * 原始文本（可选）
     */
    private String text;

    public static SentimentResult fromScore(double score) {
        String label;
        if (score > 0.1) {
            label = "positive";
        } else if (score < -0.1) {
            label = "negative";
        } else {
            label = "neutral";
        }
        return SentimentResult.builder()
                .score(score)
                .label(label)
                .build();
    }
}
