package com.stock.tradingExecutor.broker;

import com.stock.tradingExecutor.entity.AccountStatus;
import com.stock.tradingExecutor.entity.OrderResult;
import com.stock.tradingExecutor.entity.Position;
import com.stock.tradingExecutor.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 中信证券适配器
 * TODO: 实现真实的券商API对接
 */
@Slf4j
@Component
public class ZXBrokerAdapter implements BrokerAdapter {
    
    @Override
    public String getName() {
        return "ZXBroker(中信证券)";
    }
    
    @Override
    public AccountStatus getAccountInfo() {
        log.info("[ZXBroker] 获取账户资金信息");
        // TODO: 实现真实的中信证券API调用
        throw new UnsupportedOperationException("中信证券API对接待实现");
    }
    
    @Override
    public BigDecimal getRealtimePrice(String stockCode) {
        log.info("[ZXBroker] 获取股票实时价格: {}", stockCode);
        // TODO: 实现真实的中信证券API调用
        throw new UnsupportedOperationException("中信证券API对接待实现");
    }
    
    @Override
    public OrderResult submitOrder(String direction, String stockCode, BigDecimal price, Integer quantity) {
        log.info("[ZXBroker] 提交委托: {} {} {} {}", direction, stockCode, price, quantity);
        // TODO: 实现真实的中信证券API调用
        throw new UnsupportedOperationException("中信证券API对接待实现");
    }
    
    @Override
    public OrderStatus queryOrderStatus(String orderId) {
        log.info("[ZXBroker] 查询委托状态: {}", orderId);
        // TODO: 实现真实的中信证券API调用
        throw new UnsupportedOperationException("中信证券API对接待实现");
    }
    
    @Override
    public Boolean cancelOrder(String orderId) {
        log.info("[ZXBroker] 撤销委托: {}", orderId);
        // TODO: 实现真实的中信证券API调用
        throw new UnsupportedOperationException("中信证券API对接待实现");
    }
    
    @Override
    public List<OrderResult> getTodayOrders() {
        // TODO: 实现真实的中信证券API调用
        return new ArrayList<>();
    }
    
    @Override
    public List<Position> getPositions() {
        // TODO: 实现真实的中信证券API调用
        return new ArrayList<>();
    }
}