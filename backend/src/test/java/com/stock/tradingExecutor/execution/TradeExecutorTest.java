package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.broker.MockBrokerAdapter;
import com.stock.tradingExecutor.config.MonitorConfig;
import com.stock.tradingExecutor.config.PollerConfig;
import com.stock.tradingExecutor.config.RiskConfig;
import com.stock.tradingExecutor.entity.OrderResult;
import com.stock.tradingExecutor.enums.OrderStatus;
import com.stock.tradingExecutor.fee.FeeCalculator;
import com.stock.tradingExecutor.risk.RiskController;
import com.stock.tradingExecutor.time.TradingTimeChecker;
import com.stock.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 交易执行器测试
 */
@ExtendWith(MockitoExtension.class)
class TradeExecutorTest {
    
    private TradeExecutor tradeExecutor;
    private MockBrokerAdapter mockBrokerAdapter;
    private RiskController riskController;
    private PriceMonitor priceMonitor;
    private OrderPoller orderPoller;
    private FeeCalculator feeCalculator;

    @Mock
    private TradingTimeChecker tradingTimeChecker;

    @Mock
    private NotificationService notificationService;
    
    @BeforeEach
    void setUp() {
        // 创建配置
        RiskConfig riskConfig = new RiskConfig();
        MonitorConfig monitorConfig = new MonitorConfig();
        PollerConfig pollerConfig = new PollerConfig();
        com.stock.tradingExecutor.config.FeeConfig feeConfig = new com.stock.tradingExecutor.config.FeeConfig();
        com.stock.tradingExecutor.config.TradingTimeConfig tradingTimeConfig = new com.stock.tradingExecutor.config.TradingTimeConfig();
        
        // 创建组件
        mockBrokerAdapter = new MockBrokerAdapter();
        feeCalculator = new FeeCalculator(feeConfig);
        // tradingTimeChecker is mocked
        riskController = new RiskController(mockBrokerAdapter, riskConfig, tradingTimeChecker);
        priceMonitor = new PriceMonitor(mockBrokerAdapter, tradingTimeChecker, monitorConfig);
        orderPoller = new OrderPoller(mockBrokerAdapter, pollerConfig);
        
        tradeExecutor = new TradeExecutor(
                riskController,
                mockBrokerAdapter,
                priceMonitor,
                orderPoller,
                feeCalculator,
                tradingTimeChecker,
                monitorConfig,
                pollerConfig,
                notificationService
        );
    }
    
    @Test
    void testExecuteBuy_Success() throws InterruptedException {
        // Mock trading time checks
        when(tradingTimeChecker.isTradingTime()).thenReturn(true);
        when(tradingTimeChecker.isPastBuyDeadLine()).thenReturn(false);

        // 设置模拟价格
        
        // 执行买入
        OrderResult result = tradeExecutor.executeBuy("000001", new BigDecimal("10000"));
        
        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals("000001", result.getStockCode());
        assertEquals("BUY", result.getDirection());
        
        // 等待成交
        Thread.sleep(3000);
        
        // 查询状态
        OrderStatus status = tradeExecutor.queryOrderStatus(result.getOrderId());
        assertTrue(status == OrderStatus.FILLED || status == OrderStatus.SUBMITTED);
    }
    
    @Test
    void testExecuteBuy_InsufficientFunds() {
        // Mock trading time checks (need to be in trading time to fail on funds)
        when(tradingTimeChecker.isTradingTime()).thenReturn(true);
        when(tradingTimeChecker.isPastBuyDeadLine()).thenReturn(false);

        // 设置模拟价格
        
        // 执行买入 (金额过大)
        OrderResult result = tradeExecutor.executeBuy("000001", new BigDecimal("999999999"));
        
        // 应该被风控拒绝
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("风控检查未通过"));
    }
    
    @Test
    void testGetAccountStatus() {
        var status = tradeExecutor.getAccountStatus();
        
        assertNotNull(status);
        assertNotNull(status.getAvailableCash());
        assertNotNull(status.getTotalAssets());
    }
    
    @Test
    void testInterrupt() {
        // 中断交易
        tradeExecutor.interrupt();
        
        // 重置
        tradeExecutor.reset();
    }
}
