package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.entities.TradingRecord;
import online.mwang.stockTrading.repositories.TradingRecordRepository;
import online.mwang.stockTrading.services.TradingRecordService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 交易记录服务实现
 */
@Service
@RequiredArgsConstructor
public class TradingRecordServiceImpl implements TradingRecordService {

    private final TradingRecordRepository tradingRecordRepository;

    @Override
    public void save(TradingRecord tradingRecord) {
        tradingRecordRepository.save(tradingRecord);
    }

    @Override
    public TradingRecord findById(Long id) {
        return tradingRecordRepository.findById(id);
    }

    @Override
    public TradingRecord update(TradingRecord tradingRecord) {
        return tradingRecordRepository.update(tradingRecord);
    }

    @Override
    public void delete(TradingRecord tradingRecord) {
        tradingRecordRepository.delete(tradingRecord);
    }

    @Override
    public List<TradingRecord> findAll() {
        return tradingRecordRepository.findAll();
    }

    @Override
    public Long count() {
        return tradingRecordRepository.count();
    }
}
