package online.mwang.stockTrading.web.service;

import online.mwang.stockTrading.web.bean.po.StockInfo;

import java.util.List;

public interface StockInfoService {
    List<StockInfo> getStockInfoList();
    StockInfo getStockInfo(String code);
    void saveStockInfo(StockInfo stockInfo);
    void updateStockInfo(StockInfo stockInfo);
}
