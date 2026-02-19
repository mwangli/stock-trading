package online.mwang.stockTrading.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.mwang.stockTrading.services.StockSelector;

import java.util.Date;
import java.util.List;

/**
 * 选股结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectResult {

    /**
     * 选股日期
     */
    private Date selectDate;

    /**
     * 最佳股票
     */
    private StockSelector.ComprehensiveScore best;

    /**
     * Top3备选
     */
    private List<StockSelector.ComprehensiveScore> top3;

    /**
     * 所有排名
     */
    private List<StockSelector.ComprehensiveScore> allRankings;

    /**
     * 选股耗时（毫秒）
     */
    private long selectionTime;

    public static SelectResult empty() {
        return SelectResult.builder()
                .selectDate(new Date())
                .build();
    }
}
