package online.mwang.stockTrading.modules.risk.enums;

/**
 * 风控等级
 */
public enum RiskLevel {
    NORMAL("正常", 0),
    WARNING("警告", 1),
    DAILY_STOP_LOSS("单日止损", 2),
    MONTHLY_CIRCUIT_BREAKER("月度熔断", 3);

    private final String description;
    private final int level;

    RiskLevel(String description, int level) {
        this.description = description;
        this.level = level;
    }

    public String getDescription() {
        return description;
    }

    public int getLevel() {
        return level;
    }

    public boolean isBlocking() {
        return level >= 2;
    }
}
