package com.stock.dataCollector.service;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 股票信息服务
 * 负责股票基础数据在MySQL中的存储和查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockInfoService {

    private final StockInfoRepository stockInfoRepository;

    /**
     * 保存或更新股票信息
     * 按code进行去重，存在则更新，不存在则插入
     */
    @Transactional
    public StockInfo saveOrUpdate(StockInfo stockInfo) {
        Optional<StockInfo> existing = stockInfoRepository.findByCode(stockInfo.getCode());
        
        if (existing.isPresent()) {
            StockInfo existingStock = existing.get();
            updateStockInfo(existingStock, stockInfo);
            return stockInfoRepository.save(existingStock);
        } else {
            return stockInfoRepository.save(stockInfo);
        }
    }

    /**
     * 批量保存或更新股票信息
     */
    @Transactional
    public int batchSaveOrUpdate(List<StockInfo> stockList) {
        int count = 0;
        for (StockInfo stock : stockList) {
            try {
                saveOrUpdate(stock);
                count++;
            } catch (Exception e) {
                log.error("保存股票 {} 失败: {}", stock.getCode(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * 更新股票信息
     */
    private void updateStockInfo(StockInfo target, StockInfo source) {
        if (source.getName() != null) {
            target.setName(source.getName());
        }
        if (source.getMarket() != null) {
            target.setMarket(source.getMarket());
        }
        if (source.getPrice() != null) {
            target.setPrice(source.getPrice());
        }
        if (source.getChangePercent() != null) {
            target.setChangePercent(source.getChangePercent());
        }
        if (source.getChangeAmount() != null) {
            target.setChangeAmount(source.getChangeAmount());
        }
        if (source.getVolume() != null) {
            target.setVolume(source.getVolume());
        }
        if (source.getAmount() != null) {
            target.setAmount(source.getAmount());
        }
        if (source.getTotalMarketValue() != null) {
            target.setTotalMarketValue(source.getTotalMarketValue());
        }
        if (source.getCirculatingMarketValue() != null) {
            target.setCirculatingMarketValue(source.getCirculatingMarketValue());
        }
        if (source.getPeRatio() != null) {
            target.setPeRatio(source.getPeRatio());
        }
        if (source.getPbRatio() != null) {
            target.setPbRatio(source.getPbRatio());
        }
        if (source.getIsSt() != null) {
            target.setIsSt(source.getIsSt());
        }
        if (source.getIsSuspended() != null) {
            target.setIsSuspended(source.getIsSuspended());
        }
        if (source.getDataSource() != null) {
            target.setDataSource(source.getDataSource());
        }
    }

    /**
     * 根据股票代码查询
     */
    public Optional<StockInfo> findByCode(String code) {
        return stockInfoRepository.findByCode(code);
    }

    /**
     * 查询所有股票
     */
    public List<StockInfo> findAll() {
        return stockInfoRepository.findAll();
    }

    /**
     * 查询所有股票代码
     */
    public List<String> findAllCodes() {
        return stockInfoRepository.findAllCodes();
    }

    /**
     * 统计总数
     */
    public long count() {
        return stockInfoRepository.count();
    }

    /**
     * 根据市场查询
     */
    public List<StockInfo> findByMarket(String market) {
        return stockInfoRepository.findByMarket(market);
    }

    /**
     * 删除所有数据
     */
    @Transactional
    public void deleteAll() {
        stockInfoRepository.deleteAll();
    }
}