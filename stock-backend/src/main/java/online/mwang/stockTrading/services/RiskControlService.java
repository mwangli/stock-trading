package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.Position;
import online.mwang.stockTrading.enums.RiskLevel;
import online.mwang.stockTrading.results.RiskCheckResult;

import java.util.List;

/**
 * 风控服务
 * 实现单日止损、月度熔断等风控功能
 */
public interface RiskControlService {

    // 风控参数
    double DAILY_STOP_LOSS = -0.03;      // 单日止损-3%
    double MONTHLY_CIRCUIT_BREAKER = -0.10;  // 月度熔断-10%

    /**
     * 买入前风控检查
     * 检查单日亏损和月度亏损是否触发风控
     * @return 风控检查结果
     */
    RiskCheckResult checkBeforeBuy();

    /**
     * 持仓风控检查（用于止损判断）
     * @param position 持仓
     * @return 风控检查结果
     */
    RiskCheckResult checkPositionForSell(Position position);

    /**
     * 是否允许卖出（始终允许）
     * @return true
     */
    default boolean canSell() {
        return true;
    }

    /**
     * 计算当日盈亏
     * @return 盈亏比例
     */
    double calculateDailyPnL();

    /**
     * 计算当月盈亏
     * @return 盈亏比例
     */
    double calculateMonthlyPnL();

    /**
     * 获取当前风控等级
     * @return 风控等级
     */
    RiskLevel getCurrentRiskLevel();

    /**
     * 是否触发单日止损
     * @return true/false
     */
    boolean isDailyStopLossTriggered();

    /**
     * 是否触发月度熔断
     * @return true/false
     */
    boolean isMonthlyCircuitBreakerTriggered();

    /**
     * 获取需要止损的持仓列表
     * @return 持仓列表
     */
    List<Position> getStopLossPositions();

    /**
     * 记录风控日志
     * @param message 日志信息
     */
    void logRiskEvent(String message);

    /**
     * 重置风控状态（人工干预后）
     */
    void resetRiskStatus();
}
