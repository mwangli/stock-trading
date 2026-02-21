package online.mwang.stockTrading.services;

import online.mwang.stockTrading.results.TradingSignal;
import online.mwang.stockTrading.services.StockSelector;
import online.mwang.stockTrading.results.SelectResult;

/**
 * 模块四：AI金融分析 - 决策引擎
 * 根据选股结果生成交易信号
 */
public interface DecisionEngine {

    /**
     * 生成交易信号
     * @param selectResult 选股结果
     * @return 交易信号
     */
    TradingSignal generateSignal(SelectResult selectResult);

    /**
     * 判断是否适合买入
     * @param score 综合评分
     * @return true/false
     */
    boolean shouldBuy(StockSelector.ComprehensiveScore score);

    /**
     * 计算买入数量
     * @param availableAmount 可用资金
     * @param stockCode 股票代码
     * @param price 当前价格
     * @return 买入数量
     */
    int calculateBuyQuantity(double availableAmount, String stockCode, double price);

    /**
     * 获取今日交易信号
     * @return 交易信号
     */
    TradingSignal getTodaySignal();

    /**
     * 保存交易信号
     * @param signal 交易信号
     */
    void saveSignal(TradingSignal signal);

    /**
     * 验证信号是否有效
     * @param signal 交易信号
     * @return true/false
     */
    boolean isSignalValid(TradingSignal signal);
}
