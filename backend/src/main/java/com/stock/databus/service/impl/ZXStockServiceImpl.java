package com.stock.databus.service.impl;

import com.stock.databus.service.IStockService;
import com.stock.databus.pojo.StockInfo;
import com.stock.databus.pojo.StockPrices;
import java.util.Collections;
import java.util.List;

/**
 * ZX 证券平台实现（初步骨架，后续接入实际平台客户端）
 */
public class ZXStockServiceImpl implements IStockService {

    @Override
    public List<StockInfo> listStocks() {
        // TODO: 集成后端证券平台客户端
        return Collections.emptyList();
    }

    @Override
    public List<StockPrices> getHistoryPrices(String symbol, String period, String startTime, String endTime) {
        // TODO: 集成后端证券平台客户端
        return Collections.emptyList();
    }

    @Override
    public List<StockInfo> getDataList() {
        // TODO: 集成后端证券平台客户端
        return Collections.emptyList();
    }

    @Override
    public StockPrices getNowPrice(String symbol) {
        // TODO: 集成后端证券平台客户端
        return null;
    }
}
