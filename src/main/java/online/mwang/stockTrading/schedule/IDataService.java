package online.mwang.stockTrading.schedule;

import com.alibaba.fastjson.JSONObject;
import online.mwang.stockTrading.web.bean.dto.DailyItem;
import online.mwang.stockTrading.web.bean.po.*;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/8 13:55
 * @description: DataService
 */
@Service
public interface IDataService {

    /**
     * 更新账户资金信息
     */
    AccountInfo getAccountInfo();

    /**
     * 获取持仓股票
     */
    List<TradingRecord> getHoldList();

    /**
     * 获取今日订单
     */
    List<OrderStatus> listTodayOrder();

    /**
     * 获取指定股票的最新实时价格
     */
    Double getNowPrice(String code);

    /**
     * 获取取消状态的订单
     */
    List<OrderStatus> listCancelOrder();

    /**
     * 提交取消订单委托
     */
    void cancelOrder(String answerNo);

    /**
     * 获取历史订单
     */
    List<OrderStatus> listHistoryOrder();

    /**
     * 查询指定单号的订单状态
     */
    String queryOrderStatus(String answerNo);

    /**
     * 等待取消订单完成，如果成功取消返回true,如果取消失败返回false
     * 不取消订单的话，不能回收资金，提交买入订单
     */
    Boolean waitOrderStatus(String answerNo);


    /**
     * 提交买卖订单，返回订单编号
     * 如果提交订单失败，返回null不要返回空串
     */
    JSONObject buySale(String type, String code, Double price, Double number);

    /**
     * 获取最新的所有股票信息
     */
    List<StockInfo> getDataList();

    /**
     * 获取某一只股票历史价格数据
     */
    List<DailyItem> getHistoryPrices(String code);

    /**
     * 获取历史订单
     */
    List<OrderInfo> getHistoryOrder();

    /**
     * 获取今日成交订单
     */
    List<OrderInfo> getTodayOrder();

    /**
     * 计算手续费
     */
    Double getPeeAmount(Double amount);

    /**
     * 取消所有订单
     */
    void cancelAllOrder();

    default Boolean inTradingTimes1() {
        String format = DateUtils.timeFormat.format(new Date());
        return format.compareTo("09:30") >= 0 && format.compareTo("11:30") <= 0;
    }

    default Boolean inTradingTimes2() {
        String format = DateUtils.timeFormat.format(new Date());
        return format.compareTo("13:00") >= 0 && format.compareTo("15:00") <= 0;
    }
}
