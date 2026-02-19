package online.mwang.stockTrading.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 持仓记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    private Long id;

    private String stockCode;

    private String stockName;

    private int quantity;

    private double avgCost;

    private double currentPrice;

    private double marketValue;

    private double unrealizedPnl;

    private double unrealizedPnlRatio;

    private String status;

    private Date openDate;

    private Date closeDate;

    private Date updateTime;

    public void calculateUnrealizedPnl() {
        this.unrealizedPnl = (currentPrice - avgCost) * quantity;
        this.unrealizedPnlRatio = (currentPrice - avgCost) / avgCost;
        this.marketValue = currentPrice * quantity;
    }
}
