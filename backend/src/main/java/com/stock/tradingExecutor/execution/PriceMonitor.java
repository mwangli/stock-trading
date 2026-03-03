package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.broker.BrokerAdapter;
import com.stock.tradingExecutor.config.MonitorConfig;
import com.stock.tradingExecutor.entity.MonitorState;
import com.stock.tradingExecutor.entity.PriceSample;
import com.stock.tradingExecutor.time.TradingTimeChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 价格监控器
 * 监控股票价格，决定最佳执行时机
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceMonitor {
    
    private final BrokerAdapter brokerAdapter;
    private final TradingTimeChecker tradingTimeChecker;
    private final MonitorConfig config;
    
    /**
     * 监控状态缓存
     */
    private final Map<String, MonitorState> monitorStates = new ConcurrentHashMap<>();
    
    /**
     * 开始价格监控
     * 
     * @param stockCode 股票代码
     * @param direction 买卖方向 BUY/SELL
     */
    public void startMonitor(String stockCode, String direction) {
        MonitorState state = new MonitorState();
        state.setStockCode(stockCode);
        state.setDirection(direction);
        state.setSamples(new ArrayList<>());
        state.setStartTime(LocalDateTime.now());
        
        monitorStates.put(stockCode, state);
        
        log.info("开始价格监控: {} {}", stockCode, direction);
    }
    
    /**
     * 采样价格
     * 
     * @param stockCode 股票代码
     */
    public void samplePrice(String stockCode) {
        MonitorState state = monitorStates.get(stockCode);
        if (state == null) {
            log.warn("未找到监控状态: {}", stockCode);
            return;
        }
        
        // 获取当前价格
        BigDecimal currentPrice = brokerAdapter.getRealtimePrice(stockCode);
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("获取价格失败: {}", stockCode);
            return;
        }
        
        // 添加采样
        PriceSample sample = new PriceSample(stockCode, currentPrice);
        state.getSamples().add(sample);
        
        // 更新均价
        state.setAvgPrice(state.calculateAvgPrice());
        
        log.info("价格采样: {} 当前价格={} 均价={} 采样次数={}", 
                stockCode, currentPrice, state.getAvgPrice(), state.getSamples().size());
    }
    
    /**
     * 计算均价
     * 
     * @param stockCode 股票代码
     * @return 均价
     */
    public BigDecimal calculateAvgPrice(String stockCode) {
        MonitorState state = monitorStates.get(stockCode);
        if (state == null || state.getSamples() == null || state.getSamples().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return state.calculateAvgPrice();
    }
    
    /**
     * 判断是否应该执行买入
     * 
     * @param stockCode 股票代码
     * @return 是否执行
     */
    public boolean shouldExecuteBuy(String stockCode) {
        MonitorState state = monitorStates.get(stockCode);
        if (state == null) {
            return false;
        }
        
        // 检查采样次数
        if (state.getSamples().size() < config.getMinSampleCount()) {
            log.debug("采样次数不足: {} < {}", state.getSamples().size(), config.getMinSampleCount());
            return false;
        }
        
        // 检查是否临近收盘
        if (tradingTimeChecker.isNearClose()) {
            log.info("临近收盘，强制执行买入: {}", stockCode);
            return true;
        }
        
        // 获取当前价格
        BigDecimal currentPrice = brokerAdapter.getRealtimePrice(stockCode);
        BigDecimal avgPrice = state.getAvgPrice();
        
        // 计算触发价格: 均价 * (1 + 阈值)
        // 买入阈值为负，如 -1%，即均价 * 0.99
        BigDecimal thresholdPrice = avgPrice.multiply(
                BigDecimal.valueOf(1 + config.getBuyThresholdPercent()));
        
        // 更新阈值价格
        state.setThresholdPrice(thresholdPrice);
        
        // 当前价格 <= 阈值价格时触发
        boolean shouldExecute = currentPrice.compareTo(thresholdPrice) <= 0;
        
        log.info("买入判断: {} 当前价格={} 均价={} 阈值价格={} 是否执行={}",
                stockCode, currentPrice, avgPrice, thresholdPrice, shouldExecute);
        
        return shouldExecute;
    }
    
    /**
     * 判断是否应该执行卖出
     * 
     * @param stockCode 股票代码
     * @return 是否执行
     */
    public boolean shouldExecuteSell(String stockCode) {
        MonitorState state = monitorStates.get(stockCode);
        if (state == null) {
            return false;
        }
        
        // 检查采样次数
        if (state.getSamples().size() < config.getMinSampleCount()) {
            log.debug("采样次数不足: {} < {}", state.getSamples().size(), config.getMinSampleCount());
            return false;
        }
        
        // 检查是否临近收盘
        if (tradingTimeChecker.isNearClose()) {
            log.info("临近收盘，强制执行卖出: {}", stockCode);
            return true;
        }
        
        // 获取当前价格
        BigDecimal currentPrice = brokerAdapter.getRealtimePrice(stockCode);
        BigDecimal avgPrice = state.getAvgPrice();
        
        // 计算触发价格: 均价 * (1 + 阈值)
        // 卖出阈值为正，如 +0.5%，即均价 * 1.005
        BigDecimal thresholdPrice = avgPrice.multiply(
                BigDecimal.valueOf(1 + config.getSellThresholdPercent()));
        
        // 更新阈值价格
        state.setThresholdPrice(thresholdPrice);
        
        // 当前价格 >= 阈值价格时触发
        boolean shouldExecute = currentPrice.compareTo(thresholdPrice) >= 0;
        
        log.info("卖出判断: {} 当前价格={} 均价={} 阈值价格={} 是否执行={}",
                stockCode, currentPrice, avgPrice, thresholdPrice, shouldExecute);
        
        return shouldExecute;
    }
    
    /**
     * 是否临近收盘
     */
    public boolean isNearClose() {
        return tradingTimeChecker.isNearClose();
    }
    
    /**
     * 停止监控
     * 
     * @param stockCode 股票代码
     */
    public void stopMonitor(String stockCode) {
        monitorStates.remove(stockCode);
        log.info("停止价格监控: {}", stockCode);
    }
    
    /**
     * 获取监控状态
     * 
     * @param stockCode 股票代码
     * @return 监控状态
     */
    public MonitorState getMonitorState(String stockCode) {
        return monitorStates.get(stockCode);
    }
    
    /**
     * 获取采样间隔(秒)
     */
    public Long getSampleIntervalSeconds() {
        return config.getSampleIntervalSeconds();
    }
    
    /**
     * 获取采样次数
     * 
     * @param stockCode 股票代码
     * @return 采样次数
     */
    public int getSampleCount(String stockCode) {
        MonitorState state = monitorStates.get(stockCode);
        return state != null && state.getSamples() != null ? state.getSamples().size() : 0;
    }
}