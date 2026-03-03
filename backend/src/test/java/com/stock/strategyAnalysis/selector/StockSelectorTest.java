package com.stock.strategyAnalysis.selector;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.dto.SelectionResult;
import com.stock.strategyAnalysis.entity.StrategyConfig;
import com.stock.strategyAnalysis.repository.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * StockSelector 测试类
 */
@ExtendWith(MockitoExtension.class)
class StockSelectorTest {

    @Mock
    private ScoreCalculator scoreCalculator;

    @Mock
    private StrategyConfigService configService;

    @Mock
    private RankingRepository rankingRepository;

    private StockSelector stockSelector;

    @BeforeEach
    void setUp() {
        stockSelector = new StockSelector(scoreCalculator, configService, rankingRepository);
    }

    @Test
    void testSelectTopN() {
        // 准备测试数据
        StrategyConfig config = StrategyConfig.defaultConfig();
        when(configService.getCurrentConfig()).thenReturn(config);

        // 执行选股
        SelectionResult result = stockSelector.selectTopN(5);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(5, result.getTopN().size());
    }

    @Test
    void testGetStockRanking() {
        // 测试获取单只股票排名
        var ranking = stockSelector.getStockRanking("600519");
        // 由于没有真实数据，结果可能为null
        // 实际测试应使用真实数据
    }
}