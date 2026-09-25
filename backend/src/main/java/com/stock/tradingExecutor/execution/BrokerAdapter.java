// AI_GENERATE_START ---
package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.BrokerFillSnapshot;
import com.stock.tradingExecutor.domain.vo.BrokerOrderSnapshot;
import com.stock.tradingExecutor.domain.vo.OrderResult;
import com.stock.tradingExecutor.domain.entity.OrderStatus;
import com.stock.tradingExecutor.domain.entity.Position;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 券商适配器接口
 * 抽象不同券商API的差异，统一接口
 *
 * @author mwangli
 * @since 2026-09-25
 */
public interface BrokerAdapter {

    /**
     * 获取券商名称
     */
    String getName();

    /**
     * 获取账户资金信息
     * @return 账户状态
     */
    AccountStatus getAccountInfo();

    /**
     * 获取股票实时价格
     * @param stockCode 股票代码
     * @return 实时价格
     */
    BigDecimal getRealtimePrice(String stockCode);

    /**
     * 提交委托订单
     * @param direction 买卖方向 BUY/SELL
     * @param stockCode 股票代码
     * @param price 委托价格
     * @param quantity 委托数量
     * @return 订单结果
     */
    OrderResult submitOrder(String direction, String stockCode, BigDecimal price, Integer quantity);

    /**
     * 查询委托状态
     * @param orderId 委托编号
     * @return 订单状态
     */
    OrderStatus queryOrderStatus(String orderId);

    /**
     * 撤销委托
     * @param orderId 委托编号
     * @return 是否成功
     */
    Boolean cancelOrder(String orderId);

    /**
     * 获取当日所有委托
     * @return 委托列表
     */
    List<OrderResult> getTodayOrders();

    /**
     * 获取持仓列表
     * @return 持仓列表
     */
    List<Position> getPositions();

    /**
     * 判断当前券商会话是否可用于账户类只读查询。
     *
     * @return 会话有效时返回 true
     */
    boolean isAuthenticated();

    /**
     * 查询当日全部委托，包含未成交、撤单和废单。
     *
     * @return 标准化委托快照
     */
    List<BrokerOrderSnapshot> getTodayOrderSnapshots();

    /**
     * 查询当日成交明细。
     *
     * @return 标准化成交快照
     */
    List<BrokerFillSnapshot> getTodayFillSnapshots();

    /**
     * 查询指定日期范围的历史委托。
     *
     * @param startDate 开始日期，包含当天
     * @param endDate   结束日期，包含当天
     * @return 标准化历史委托快照
     */
    List<BrokerOrderSnapshot> getHistoryOrderSnapshots(LocalDate startDate, LocalDate endDate);

    /**
     * 查询指定日期范围的历史成交。
     *
     * @param startDate 开始日期，包含当天
     * @param endDate   结束日期，包含当天
     * @return 标准化历史成交快照
     */
    List<BrokerFillSnapshot> getHistoryFillSnapshots(LocalDate startDate, LocalDate endDate);
}
// AI_GENERATE_END ---
