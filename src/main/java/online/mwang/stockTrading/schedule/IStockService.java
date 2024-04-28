package online.mwang.stockTrading.schedule;

import com.alibaba.fastjson.JSONObject;
import online.mwang.stockTrading.web.bean.dto.DailyItem;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/8 13:55
 * @description: DataService
 */
@Service
public interface IStockService {

    /**
     * 获取账户资金信息
     */
    AccountInfo updateAccountInfo();


    /**
     * 获取最新的所有股票信息
     */
    List<StockInfo> getDataList();

    /**
     * 获取指定股票的最新实时价格
     */
    Double getNowPrice(String code);

    /**
     * 获取某一只股票历史价格数据
     */
    List<DailyItem> getHistoryPrices(String code);

    /**
     * 获取历史已成交订单
     */
    List<OrderInfo> getHistoryOrder();

    /**
     * 获取今日成交订单
     */
    List<OrderInfo> getTodayOrder();


    /**
     * 撤销今日所有无效订单
     */
    Integer cancelAllOrder();

    /**
     * 计算手续费
     */
    Double getPeeAmount(Double amount);

    /**
     * 提交买卖订单，返回订单编号
     */
    JSONObject buySale(String type, String code, Double price, Double number);


    /**
     * 等待买入或者卖出订单完成
     */
    Boolean waitOrderStatus(String answerNo);
}
