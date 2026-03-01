package com.stock.dataCollector.service;

import com.stock.dataCollector.entity.mysql.StockInfoMySql;
import com.stock.dataCollector.repository.mysql.StockInfoMySqlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 股票信息MySQL服务
 * 负责股票基础数据在MySQL中的存储和查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockInfoMySqlService {

    private final StockInfoMySqlRepository stockInfoMySqlRepository;

    /**
     * 保存或更新股票信息
     * 按code进行去重，存在则更新，不存在则插入
     */
    @Transactional
    public StockInfoMySql saveOrUpdate(StockInfoMySql stockInfo) {
        Optional<StockInfoMySql> existing = stockInfoMySqlRepository.findByCode(stockInfo.getCode());
        
        if (existing.isPresent()) {
            // 更新现有记录
            StockInfoMySql existingStock = existing.get();
            updateStockInfo(existingStock, stockInfo);
            return stockInfoMySqlRepository.save(existingStock);
        } else {
            // 插入新记录
            return stockInfoMySqlRepository.save(stockInfo);
        }
    }

    /**
     * 批量保存或更新股票信息
     */
    @Transactional
    public int batchSaveOrUpdate(List<StockInfoMySql> stockList) {
        int count = 0;
        for (StockInfoMySql stock : stockList) {
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
    private void updateStockInfo(StockInfoMySql target, StockInfoMySql source) {
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
    public Optional<StockInfoMySql> findByCode(String code) {
        return stockInfoMySqlRepository.findByCode(code);
    }

    /**
     * 查询所有股票
     */
    public List<StockInfoMySql> findAll() {
        return stockInfoMySqlRepository.findAll();
    }

    /**
     * 查询所有股票代码
     */
    public List<String> findAllCodes() {
        return stockInfoMySqlRepository.findAllCodes();
    }

    /**
     * 统计总数
     */
    public long count() {
        return stockInfoMySqlRepository.count();
    }

    /**
     * 根据市场查询
     */
    public List<StockInfoMySql> findByMarket(String market) {
        return stockInfoMySqlRepository.findByMarket(market);
    }

    /**
     * 删除所有数据
     */
    @Transactional
    public void deleteAll() {
        stockInfoMySqlRepository.deleteAll();
    }
}