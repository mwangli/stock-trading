package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.entities.StockInfo;
import online.mwang.stockTrading.repositories.StockInfoRepository;
import online.mwang.stockTrading.services.StockInfoService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 股票信息服务实现
 */
@Service
@RequiredArgsConstructor
public class StockInfoServiceImpl implements StockInfoService {

    private final StockInfoRepository stockInfoRepository;

    @Override
    public void save(StockInfo stockInfo) {
        stockInfoRepository.save(stockInfo);
    }

    @Override
    public StockInfo findById(Long id) {
        return stockInfoRepository.findById(id);
    }

    @Override
    public StockInfo findByCode(String code) {
        return stockInfoRepository.findByCode(code);
    }

    @Override
    public String findNameByCode(String code) {
        return stockInfoRepository.findNameByCode(code);
    }

    @Override
    public List<StockInfo> findAll() {
        return stockInfoRepository.findAll();
    }

    @Override
    public int deleteByCode(String code) {
        return stockInfoRepository.deleteByCode(code);
    }

    @Override
    public StockInfo update(StockInfo stockInfo) {
        return stockInfoRepository.update(stockInfo);
    }

    @Override
    public Long count() {
        return stockInfoRepository.count();
    }
}
