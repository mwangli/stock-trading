package online.mwang.stockTrading.modules.trading;

import com.alibaba.fastjson.JSONObject;
import online.mwang.stockTrading.domain.entity.AccountInfo;
import online.mwang.stockTrading.domain.entity.OrderInfo;
import online.mwang.stockTrading.domain.entity.Position;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 交易执行服务
 * 负责对接券商API执行买卖操作
 * 
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/8 13:55
 * @description: TradeExecutionService
 */
@Service
public interface TradeExecutionService {

    /**
     * 执行买入（T+1建仓）- 受风控限制
     * 
     * @param stockCode 股票代码
     * @param quantity 买入数量
     * @return 订单结果
     */
    OrderResult executeBuy(String stockCode, int quantity);

    /**
     * 执行卖出（T+1平仓）- 始终允许，优先释放资金
     * 不受风控限制，止损必须执行
     * 
     * @param stockCode 股票代码
     * @param quantity 卖出数量
     * @return 订单结果
     */
    OrderResult executeSell(String stockCode, int quantity);

    /**
     * 获取当前持仓列表
     * 
     * @return 持仓列表
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
