package online.mwang.stockTrading.modules.trading.service;

import com.alibaba.fastjson2.JSONObject;
import online.mwang.stockTrading.modules.trading.entity.AccountInfo;
import online.mwang.stockTrading.modules.trading.entity.OrderInfo;
import online.mwang.stockTrading.modules.trading.entity.Position;
import online.mwang.stockTrading.modules.trading.model.OrderResult;

import java.util.List;

/**
 * 交易执行服务接口
 */
public interface TradeExecutionService {

    /**
     * 执行买入（T+1建仓）- 受风控限制
     */
    OrderResult executeBuy(String stockCode, int quantity);

    /**
     * 执行卖出（T+1平仓）- 始终允许，优先释放资金
     */
    OrderResult executeSell(String stockCode, int quantity);

    /**
     * 获取当前持仓列表
     */
    List<Position> getHoldingPositions();

    /**
     * 重试卖出（用于止损场景）
     */
    void retrySellIfNeeded();

    /**
     * 获取账户资金信息
     */
    AccountInfo getAccountInfo();

    /**
     * 获取今日成交订单
     */
    List<OrderInfo> getTodayOrder();

    /**
     * 获取历史已成交订单
     */
    List<OrderInfo> getHistoryOrder();

    /**
     * 撤销今日所有无效订单
     */
    Integer cancelAllOrder();

    /**
     * 计算手续费
     */
    Double getFeeAmount(Double amount);

    /**
     * 提交买卖订单
     */
    JSONObject submitOrder(String type, String code, Double price, Double number);

    /**
     * 等待订单完成
     */
    boolean waitOrderSuccess(String answerNo);

    /**
     * 获取指定股票的最新实时价格
     */
    Double getCurrentPrice(String code);

    /**
     * 获取Token
     */
    String getToken();

    /**
     * 设置Token
     */
    void setToken(String token);
}
