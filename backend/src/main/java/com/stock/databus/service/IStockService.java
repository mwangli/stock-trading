package com.stock.databus.service;

import java.util.List;

import com.stock.databus.pojo.StockInfo;
import com.stock.databus.pojo.StockPrices;

/**
 * 证券平台数据服务接口（后台数据采集用）
 * 该接口作为对接证券平台的抽象，后续实现通过具体平台客户端完成数据抓取。
 */
public interface IStockService {
    /** 获取证券列表信息 */
    List<StockInfo> listStocks();

    /** 获取历史 K 线数据 */
    List<StockPrices> getHistoryPrices(String symbol, String period, String startTime, String endTime);

    /** 获取股票数据列表，用于股票同步 */
    List<StockInfo> getDataList();

    /** 获取当前实时价格 */
    StockPrices getNowPrice(String symbol);
}
