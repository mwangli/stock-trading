package online.mwang.stockTrading.web.service;

import online.mwang.stockTrading.entities.TradingRecord;

import java.util.List;

public interface TradingRecordService {
    List<TradingRecord> getTradingRecords();
    TradingRecord getTradingRecordById(Long id);
    void saveTradingRecord(TradingRecord tradingRecord);
    void updateTradingRecord(TradingRecord tradingRecord);
    void deleteTradingRecord(Long id);
}
