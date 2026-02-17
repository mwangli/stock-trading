package online.mwang.stockTrading.modules.dexter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Dexter分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DexterResult {

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 分析类型
     */
    private String analysisType;

    /**
     * 分析结果/建议
     */
    private String answer;

    /**
     * 详细分析
     */
    private String detail;

    /**
     * 评分 (0-1)
     */
    private double score;

    /**
     * 建议标签：BUY / SELL / HOLD
     */
    private String label;

    /**
     * 分析时间
     */
    private Date analysisTime;

    /**
     * 数据来源
     */
    private String source;

    public static DexterResult bullish(String stockCode, String answer) {
        return DexterResult.builder()
                .stockCode(stockCode)
                .answer(answer)
                .label("BUY")
                .score(0.8)
                .analysisTime(new Date())
                .build();
    }

    public static DexterResult bearish(String stockCode, String answer) {
        return DexterResult.builder()
                .stockCode(stockCode)
                .answer(answer)
                .label("SELL")
                .score(0.2)
                .analysisTime(new Date())
                .build();
    }
}
