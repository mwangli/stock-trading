package com.stock.strategyAnalysis.intraday;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.entity.StrategyConfig;
import com.stock.strategyAnalysis.repository.DecisionRepository;
import com.stock.strategyAnalysis.intraday.BollingerCalculator;
import com.stock.strategyAnalysis.intraday.DecisionAggregator;
import com.stock.strategyAnalysis.intraday.VolumeCalculator;
import com.stock.strategyAnalysis.intraday.RsiCalculator;
import com.stock.strategyAnalysis.intraday.TrailingStopCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * IntradaySellService 测试类
 */
@ExtendWith(MockitoExtension.class)
class IntradaySellServiceTest {

    @Mock
    private TrailingStopCalculator trailingStopCalculator;

    @Mock
    private RsiCalculator rsiCalculator;

    @Mock
    private VolumeCalculator volumeCalculator;

    @Mock
    private BollingerCalculator bollingerCalculator;

    @Mock
    private DecisionAggregator decisionAggregator;

    @Mock
    private StrategyConfigService configService;

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private IntradaySellService intradaySellService;

    @BeforeEach
    void setUp() {
        intradaySellService = new IntradaySellService(
                trailingStopCalculator,
                rsiCalculator,
                volumeCalculator,
                bollingerCalculator,
                decisionAggregator,
                configService,
                decisionRepository,
                redisTemplate
        );
    }

    @Test
    void testIsForceSellTime() {
        // 测试强制卖出时间判断
        // 由于无法轻易Mock系统时间，这里根据当前时间动态断言
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.LocalTime forceTime = java.time.LocalTime.of(14, 57);
        boolean expected = now.isAfter(forceTime) || now.equals(forceTime);
        
        assertEquals(expected, intradaySellService.isForceSellTime(), 
            "isForceSellTime should return " + expected + " at " + now);
    }

    @Test
    void testForceSell() {
        // 测试强制卖出
        var decision = intradaySellService.forceSell("600519", "测试强制卖出");

        assertNotNull(decision);
        assertTrue(decision.isShouldSell());
        assertEquals("600519", decision.getStockCode());
    }
}
