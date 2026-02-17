package online.mwang.stockTrading.modules.decision.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.mwang.stockTrading.modules.decision.enums.Signal;

import java.util.Date;

/**
 * 交易信号
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingSignal {

    /**
     * 信号类型：BUY / HOLD
     */
    private Signal signal;

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 置信度 (0-1)
     */
    private double confidence;

    /**
     * 决策原因
     */
    private String reason;

    /**
     * 建议买入价格
     */
    private double suggestedPrice;

    /**
     * 建议买入数量
     */
    private int suggestedQuantity;

    /**
     * 信号生成时间
     */
    private Date generateTime;

    /**
     * 信号有效期（仅当日有效）
     */
    private Date validUntil;

    /**
     * 关联的选股ID
     */
    private Long selectionId;

    public static TradingSignal buy(String stockCode, String stockName, double confidence, String reason) {
        return TradingSignal.builder()
                .signal(Signal.BUY)
                .stockCode(stockCode)
                .stockName(stockName)
                .confidence(confidence)
                .reason(reason)
                .generateTime(new Date())
                .build();
    }

    public static TradingSignal hold(String reason) {
        return TradingSignal.builder()
                .signal(Signal.HOLD)
                .reason(reason)
                .generateTime(new Date())
                .build();
    }

    public boolean isBuy() {
        return Signal.BUY.equals(this.signal);
    }
}
