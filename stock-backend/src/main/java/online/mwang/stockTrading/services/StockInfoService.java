package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.StockInfo;

import java.util.List;

/**
 * 股票信息服务接口
 */
public interface StockInfoService {

    void save(StockInfo stockInfo);

    StockInfo findById(Long id);

    StockInfo findByCode(String code);

    String findNameByCode(String code);

    List<StockInfo> findAll();

    int deleteByCode(String code);

    StockInfo update(StockInfo stockInfo);

    Long count();
}
