package com.stock.strategyAnalysis.persistence;

import com.stock.strategyAnalysis.domain.entity.Signal;
import com.stock.strategyAnalysis.domain.entity.TradingSignal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易信号Repository
 * 使用MongoDB存储信号记录
 */
@Repository
public interface SignalRepository extends MongoRepository<TradingSignal, String> {

    /**
     * 根据信号类型查询最新信号
     */
    List<TradingSignal> findBySignalTypeOrderByGenerateTimeDesc(Signal signalType);

    /**
     * 查询未执行的信号
     */
    List<TradingSignal> findByExecutedFalse();

    /**
     * 根据股票代码查询最新信号
     */
    TradingSignal findFirstByStockCodeOrderByGenerateTimeDesc(String stockCode);

    /**
     * 根据生成日期查询信号
     */
    List<TradingSignal> findByGenerateTimeBetween(LocalDate startTime, LocalDate endTime);

    /**
     * 根据信号类型和生成日期查询
     */
    List<TradingSignal> findBySignalTypeAndGenerateTimeBetween(
            Signal signalType,
            LocalDate startTime,
            LocalDate endTime
    );
}
