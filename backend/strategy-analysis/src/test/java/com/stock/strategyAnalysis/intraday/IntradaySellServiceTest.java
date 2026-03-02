package com.stock.strategyAnalysis.intraday;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.entity.StrategyConfig;
import com.stock.strategyAnalysis.intraday.calculator.*;
import com.stock.strategyAnalysis.intraday.decision.DecisionAggregator;
import com.stock.strategyAnalysis.repository.DecisionRepository;
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
        // 实际测试应模拟不同的时间
        assertFalse(intradaySellService.isForceSellTime());
    }

    @Test
    void testForceSell() {
        StrategyConfig config = StrategyConfig.defaultConfig();
        when(configService.getCurrentConfig()).thenReturn(config);

        // 测试强制卖出
        var decision = intradaySellService.forceSell("600519", "测试强制卖出");
        
        assertNotNull(decision);
        assertTrue(decision.isShouldSell());
        assertEquals("600519", decision.getStockCode());
    }
}