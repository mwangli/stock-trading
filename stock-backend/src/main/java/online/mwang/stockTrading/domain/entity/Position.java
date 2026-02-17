package online.mwang.stockTrading.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 持仓记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    private Long id;

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 持仓数量
     */
    private int quantity;

    /**
     * 平均成本
     */
    private double avgCost;

    /**
     * 当前价格
     */
    private double currentPrice;

    /**
     * 市值
     */
    private double marketValue;

    /**
     * 浮动盈亏
     */
    private double unrealizedPnl;

    /**
     * 浮动盈亏比例
     */
    private double unrealizedPnlRatio;

    /**
     * 状态：HOLDING/SOLD
     */
    private String status;

    /**
     * 建仓日期
     */
    private Date openDate;

    /**
     * 平仓日期
     */
    private Date closeDate;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 计算浮动盈亏
     */
    public void calculateUnrealizedPnl() {
        this.unrealizedPnl = (currentPrice - avgCost) * quantity;
        this.unrealizedPnlRatio = (currentPrice - avgCost) / avgCost;
        this.marketValue = currentPrice * quantity;
    }
}
