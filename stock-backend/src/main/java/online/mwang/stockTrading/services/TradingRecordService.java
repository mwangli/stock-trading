package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.TradingRecord;

import java.util.List;

/**
 * 交易记录服务接口
 */
public interface TradingRecordService {

    void save(TradingRecord tradingRecord);

    TradingRecord findById(Long id);

    TradingRecord update(TradingRecord tradingRecord);

    void delete(TradingRecord tradingRecord);

    List<TradingRecord> findAll();

    Long count();
}
