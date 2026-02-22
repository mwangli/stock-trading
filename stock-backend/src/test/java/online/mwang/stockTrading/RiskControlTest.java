package online.mwang.stockTrading;

import online.mwang.stockTrading.entities.Position;
import online.mwang.stockTrading.enums.Action;
import online.mwang.stockTrading.enums.RiskLevel;
import online.mwang.stockTrading.results.RiskCheckResult;
import online.mwang.stockTrading.services.impl.RiskControlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 风控管理模块测试验证
 * 模块七：风控管理
 * 
 * 验收标准：
 * 1. 日止损检查 - 亏损>=3%触发
 * 2. 月熔断检查 - 亏损>=10%触发
 * 3. 卖出独立性 - 卖出不受限制
 * 4. 风控日志记录 - 完整记录
 * 5. 单元测试覆盖率 > 80%
 */
@DisplayName("风控管理模块 - 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RiskControlTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ListOperations<String, Object> listOperations;

    private RiskControlServiceImpl riskControlService;

    @BeforeEach
    void setUp() {
        riskControlService = new RiskControlServiceImpl(null, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    // ==================== FR-001: 日止损检查测试 ====================

    /**
     * 测试用例: TC-RISK-001
     * 功能: 日止损检查 - 亏损>=3%触发日止损
     * 验证: 当日亏损达到3%时，checkBeforeBuy返回失败
     * 
     * 规则：单日亏损 >= 3% → 禁止新买入
     */
    @Test
    @DisplayName("TC-RISK-001: 日止损触发 - 亏损达到3%")
    void testDailyStopLossTriggered() {
        when(valueOperations.get(anyString())).thenReturn(-0.03);

        RiskCheckResult result = riskControlService.checkBeforeBuy();

        assertNotNull(result);
        assertFalse(result.isPassed());
        assertEquals(Action.BLOCK, result.getAction());
        assertTrue(result.getMessage().contains("Daily stop loss"));
    }

    /**
     * 测试用例: TC-RISK-002
     * 功能: 日止损检查 - 亏损超过3%触发
     * 验证: 当日亏损超过3%时，触发日止损
     */
    @Test
    @DisplayName("TC-RISK-002: 日止损触发 - 亏损超过3%")
    void testDailyStopLossTriggeredExceed() {
        when(valueOperations.get(anyString())).thenReturn(-0.05);

        boolean triggered = riskControlService.isDailyStopLossTriggered();

        assertTrue(triggered, "亏损-5%应该触发日止损");
    }

    /**
     * 测试用例: TC-RISK-003
     * 功能: 日止损检查 - 亏损未达到3%通过
     * 验证: 当日亏损小于3%时，风控检查通过
     */
    @Test
    @DisplayName("TC-RISK-003: 日止损未触发 - 亏损低于3%")
    void testDailyStopLossNotTriggered() {
        when(valueOperations.get(anyString())).thenReturn(-0.02);

        RiskCheckResult result = riskControlService.checkBeforeBuy();

        assertNotNull(result);
        assertTrue(result.isPassed());
        assertEquals(Action.ALLOW, result.getAction());
    }

    /**
     * 测试用例: TC-RISK-004
     * 功能: 日止损计算正确性
     * 验证: calculateDailyPnL返回正确的盈亏值
     */
    @Test
    @DisplayName("TC-RISK-004: 日盈亏计算")
    void testCalculateDailyPnL() {
        when(valueOperations.get(anyString())).thenReturn(-0.015);

        double dailyPnL = riskControlService.calculateDailyPnL();

        assertEquals(-0.015, dailyPnL, 0.001);
    }

    // ==================== FR-002: 月熔断检查测试 ====================

    /**
     * 测试用例: TC-RISK-005
     * 功能: 月熔断检查 - 亏损>=10%触发月熔断
     * 验证: 当月亏损达到10%时，触发月熔断
     * 
     * 规则：单月亏损 >= 10% → 触发熔断
     */
    @Test
    @DisplayName("TC-RISK-005: 月熔断触发 - 亏损达到10%")
    void testMonthlyCircuitBreakerTriggered() {
        when(valueOperations.get(anyString())).thenReturn(-0.10);

        boolean triggered = riskControlService.isMonthlyCircuitBreakerTriggered();

        assertTrue(triggered, "亏损-10%应该触发月熔断");
    }

    /**
     * 测试用例: TC-RISK-006
     * 功能: 月熔断检查 - 亏损超过10%触发
     * 验证: 当月亏损超过10%时，触发月熔断
     * 注意：当前实现先检查日止损，再检查月熔断
     */
    @Test
    @DisplayName("TC-RISK-006: 月熔断触发 - 亏损超过10%")
    void testMonthlyCircuitBreakerTriggeredExceed() {
        // 先返回-2%（不触发日止损），再返回-15%（触发月熔断）
        when(valueOperations.get(anyString()))
            .thenReturn(-0.02)  // 日止损
            .thenReturn(-0.15); // 月熔断

        RiskCheckResult result = riskControlService.checkBeforeBuy();

        assertNotNull(result);
        assertFalse(result.isPassed());
        assertEquals(Action.BLOCK, result.getAction());
        assertTrue(result.getMessage().contains("Monthly circuit breaker"));
    }

    /**
     * 测试用例: TC-RISK-007
     * 功能: 月熔断检查 - 亏损未达到10%通过
     * 验证: 当月亏损小于10%时，checkBeforeBuy通过
     */
    @Test
    @DisplayName("TC-RISK-007: 月熔断未触发 - 亏损低于10%")
    void testMonthlyCircuitBreakerNotTriggered() {
        // 先返回-2%（不触发日止损），再返回-5%（不触发月熔断）
        when(valueOperations.get(anyString()))
            .thenReturn(-0.02)  // 日止损
            .thenReturn(-0.05); // 月熔断

        RiskCheckResult result = riskControlService.checkBeforeBuy();

        assertNotNull(result);
        assertTrue(result.isPassed());
    }

    /**
     * 测试用例: TC-RISK-008
     * 功能: 日止损优先于月熔断检查
     * 验证: 日止损触发时，优先返回日止损信息
     * 注意：当前实现是日止损先检查
     */
    @Test
    @DisplayName("TC-RISK-008: 日止损优先检查")
    void testDailyStopLossPriority() {
        // 模拟日亏损-5%（触发日止损），月亏损-12%（触发月熔断）
        // 日止损应该优先返回
        when(valueOperations.get(anyString()))
            .thenReturn(-0.05)  // 日止损
            .thenReturn(-0.12); // 月熔断

        RiskCheckResult result = riskControlService.checkBeforeBuy();

        assertNotNull(result);
        assertFalse(result.isPassed());
        // 日止损优先检查
        assertTrue(result.getMessage().contains("Daily stop loss"));
    }

    /**
     * 测试用例: TC-RISK-009
     * 功能: 月盈亏计算正确性
     * 验证: calculateMonthlyPnL返回正确的盈亏值
     */
    @Test
    @DisplayName("TC-RISK-009: 月盈亏计算")
    void testCalculateMonthlyPnL() {
        when(valueOperations.get(anyString())).thenReturn(-0.08);

        double monthlyPnL = riskControlService.calculateMonthlyPnL();

        assertEquals(-0.08, monthlyPnL, 0.001);
    }

    // ==================== FR-003: 卖出独立性保障测试 ====================

    /**
     * 测试用例: TC-RISK-010
     * 功能: 卖出独立性 - 始终允许卖出
     * 验证: canSell()始终返回true
     * 
     * 规则：风控只限制买入，不限制卖出
     */
    @Test
    @DisplayName("TC-RISK-010: 卖出独立性 - 始终允许卖出")
    void testCanSellAlways() {
        boolean canSell = riskControlService.canSell();
        assertTrue(canSell, "卖出应该始终被允许");
    }

    /**
     * 测试用例: TC-RISK-011
     * 功能: 止损卖出检查
     * 验证: 持仓亏损超过3%时，触发强制卖出
     */
    @Test
    @DisplayName("TC-RISK-011: 止损卖出检查 - 触发强制卖出")
    void testStopLossForceSell() {
        Position position = createPosition("000001", "测试股票", 1000, 10.0, 9.5);

        RiskCheckResult result = riskControlService.checkPositionForSell(position);

        assertNotNull(result);
        assertTrue(result.shouldForceSell(), "亏损-5%应该触发强制卖出");
        assertEquals(Action.FORCE_SELL, result.getAction());
    }

    /**
     * 测试用例: TC-RISK-012
     * 功能: 盈利持仓卖出检查
     * 验证: 持仓盈利时，checkPositionForSell通过
     */
    @Test
    @DisplayName("TC-RISK-012: 盈利持仓 - 卖出通过")
    void testProfitPositionSell() {
        Position position = createPosition("000001", "测试股票", 1000, 10.0, 10.5);

        RiskCheckResult result = riskControlService.checkPositionForSell(position);

        assertNotNull(result);
        assertTrue(result.isPassed(), "盈利持仓应该可以卖出");
    }

    /**
     * 测试用例: TC-RISK-013
     * 功能: 亏损不足3%不触发止损
     * 验证: 亏损小于3%时，不触发强制卖出
     */
    @Test
    @DisplayName("TC-RISK-013: 轻微亏损 - 不触发止损")
    void testMinorLossNotTrigger() {
        Position position = createPosition("000001", "测试股票", 1000, 10.0, 9.8);

        RiskCheckResult result = riskControlService.checkPositionForSell(position);

        assertNotNull(result);
        assertFalse(result.shouldForceSell(), "亏损-2%不应该触发强制卖出");
    }

    // ==================== FR-004: 风控等级测试 ====================

    /**
     * 测试用例: TC-RISK-014
     * 功能: 风控等级 - 正常
     * 验证: 盈亏正常时，返回NORMAL等级
     */
    @Test
    @DisplayName("TC-RISK-014: 风控等级 - 正常")
    void testRiskLevelNormal() {
        when(valueOperations.get(anyString()))
            .thenReturn(0.01)   // 日盈利
            .thenReturn(0.02);   // 月盈利

        RiskLevel level = riskControlService.getCurrentRiskLevel();

        assertEquals(RiskLevel.NORMAL, level);
    }

    /**
     * 测试用例: TC-RISK-015
     * 功能: 风控等级 - 警告
     * 验证: 亏损达到警告阈值时，返回WARNING等级
     */
    @Test
    @DisplayName("TC-RISK-015: 风控等级 - 警告")
    void testRiskLevelWarning() {
        when(valueOperations.get(anyString()))
            .thenReturn(-0.02)  // 日亏损
            .thenReturn(-0.06); // 月亏损

        RiskLevel level = riskControlService.getCurrentRiskLevel();

        assertEquals(RiskLevel.WARNING, level);
    }

    /**
     * 测试用例: TC-RISK-016
     * 功能: 风控等级 - 日止损
     * 验证: 日亏损>=3%时，返回DAILY_STOP_LOSS等级
     */
    @Test
    @DisplayName("TC-RISK-016: 风控等级 - 日止损")
    void testRiskLevelDailyStopLoss() {
        when(valueOperations.get(anyString()))
            .thenReturn(-0.04)  // 日亏损
            .thenReturn(-0.05); // 月亏损

        RiskLevel level = riskControlService.getCurrentRiskLevel();

        assertEquals(RiskLevel.DAILY_STOP_LOSS, level);
    }

    /**
     * 测试用例: TC-RISK-017
     * 功能: 风控等级 - 月熔断（最高优先级）
     * 验证: 月亏损>=10%时，返回MONTHLY_CIRCUIT_BREAKER等级
     */
    @Test
    @DisplayName("TC-RISK-017: 风控等级 - 月熔断")
    void testRiskLevelMonthlyCircuitBreaker() {
        when(valueOperations.get(anyString()))
            .thenReturn(-0.02)  // 日亏损
            .thenReturn(-0.12); // 月亏损

        RiskLevel level = riskControlService.getCurrentRiskLevel();

        assertEquals(RiskLevel.MONTHLY_CIRCUIT_BREAKER, level);
    }

    /**
     * 测试用例: TC-RISK-018
     * 功能: 风控等级isBlocking判断
     * 验证: 日止损和月熔断应该被判定为blocking
     */
    @Test
    @DisplayName("TC-RISK-018: 风控等级blocking判断")
    void testRiskLevelBlocking() {
        assertTrue(RiskLevel.DAILY_STOP_LOSS.isBlocking());
        assertTrue(RiskLevel.MONTHLY_CIRCUIT_BREAKER.isBlocking());
        assertFalse(RiskLevel.NORMAL.isBlocking());
        assertFalse(RiskLevel.WARNING.isBlocking());
    }

    // ==================== FR-006: 风控日志记录测试 ====================

    /**
     * 测试用例: TC-RISK-019
     * 功能: 风控日志记录
     * 验证: logRiskEvent正确记录日志
     */
    @Test
    @DisplayName("TC-RISK-019: 风控日志记录")
    void testLogRiskEvent() {
        String testMessage = "Test risk event";

        riskControlService.logRiskEvent(testMessage);

        verify(redisTemplate).opsForList();
    }

    /**
     * 测试用例: TC-RISK-020
     * 功能: 风控状态重置
     * 验证: resetRiskStatus清除缓存状态
     */
    @Test
    @DisplayName("TC-RISK-020: 风控状态重置")
    void testResetRiskStatus() {
        riskControlService.resetRiskStatus();

        verify(redisTemplate, times(2)).delete(anyString());
    }

    // ==================== 边界测试 ====================

    /**
     * 测试用例: TC-RISK-021
     * 功能: 日止损边界值 - 正好-3%
     * 验证: 亏损正好-3%时触发日止损
     */
    @Test
    @DisplayName("TC-RISK-021: 日止损边界值 - 正好-3%")
    void testDailyStopLossBoundary() {
        when(valueOperations.get(anyString())).thenReturn(-0.03);

        boolean triggered = riskControlService.isDailyStopLossTriggered();

        assertTrue(triggered, "亏损正好-3%应该触发日止损");
    }

    /**
     * 测试用例: TC-RISK-022
     * 功能: 月熔断边界值 - 正好-10%
     * 验证: 亏损正好-10%时触发月熔断
     */
    @Test
    @DisplayName("TC-RISK-022: 月熔断边界值 - 正好-10%")
    void testMonthlyCircuitBreakerBoundary() {
        when(valueOperations.get(anyString())).thenReturn(-0.10);

        boolean triggered = riskControlService.isMonthlyCircuitBreakerTriggered();

        assertTrue(triggered, "亏损正好-10%应该触发月熔断");
    }

    /**
     * 测试用例: TC-RISK-023
     * 功能: 盈利场景 - 日盈利
     * 验证: 日盈利时checkBeforeBuy通过
     */
    @Test
    @DisplayName("TC-RISK-023: 盈利场景 - 日盈利")
    void testDailyProfit() {
        when(valueOperations.get(anyString())).thenReturn(0.05);

        RiskCheckResult result = riskControlService.checkBeforeBuy();

        assertNotNull(result);
        assertTrue(result.isPassed());
        assertEquals(Action.ALLOW, result.getAction());
    }

    /**
     * 测试用例: TC-RISK-024
     * 功能: 盈利场景 - 月盈利
     * 验证: 月盈利时checkBeforeBuy通过
     */
    @Test
    @DisplayName("TC-RISK-024: 盈利场景 - 月盈利")
    void testMonthlyProfit() {
        when(valueOperations.get(anyString()))
            .thenReturn(0.01)   // 日盈利
            .thenReturn(0.08);  // 月盈利

        RiskCheckResult result = riskControlService.checkBeforeBuy();

        assertNotNull(result);
        assertTrue(result.isPassed());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用Position对象
     */
    private Position createPosition(String stockCode, String stockName, 
                                    int quantity, double avgCost, double currentPrice) {
        return Position.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .quantity(quantity)
                .avgCost(avgCost)
                .currentPrice(currentPrice)
                .build();
    }
}
