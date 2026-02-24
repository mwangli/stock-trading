package online.mwang.stockTrading.schedule;

import com.alibaba.fastjson2.JSONObject;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;

import java.util.List;

public interface IStockService {
    
    Integer cancelAllOrder();
    
    AccountInfo getAccountInfo();
    
    List<OrderInfo> getTodayOrder();
    
    List<OrderInfo> getHistoryOrder();
    
    Double getNowPrice(String code);
    
    JSONObject buySale(String type, String code, Double price, Double number);
    
    boolean waitSuccess(String answerNo);
    
    List<StockInfo> getDataList();
    
    List<StockPrices> getHistoryPrices(String code);
    
    Double getPeeAmount(Double amount);
    
    String getToken();
    
    void setToken(String token);
}
