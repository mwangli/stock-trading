package online.mwang.stockTrading.predict.data;

import lombok.Data;

/**
 * Created by zhanghao on 26/7/17.
 *
 * @author ZHANG HAO
 */
@Data
public class StockData {
    private String date;
    private String code;

    // 上午开盘价
    // 目前阶段是用开盘价曲线来预测T+1的开盘价，以指导T交易日的股票选择策略
    // 如果使用下面所有的价格来预测，则需要等T交易日的所有价格数据拿到，
    // 此时已经无法在T交易日买入股票，只能在T+1买入，那么卖出时间则为T+2
    // 会大幅度降低LSTM模型的准确性，也失去价格指导的意义
    // 目前的策略是做短线交易，今天买，明天卖，预测明天开盘价来指导今天的股票买入
    // 准确来说是上午卖出，下午买入，中午拿到今天的开盘价来预测明天的开盘价(可以多只股票并行买入卖出操作以降低风险)
    // 如果同时进行买入，卖出操作，则同一时刻，持仓一股，空闲一股，资金利用率只有50%
    private double price1;
    // 上午收盘价
    private double price2;
    // 下午开盘价
    private double price3;
    // 下午收盘价
    private double price4;
}
