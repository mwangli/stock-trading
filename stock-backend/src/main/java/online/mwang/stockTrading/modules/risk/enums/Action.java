package online.mwang.stockTrading.modules.risk.enums;

/**
 * 风控触发的动作
 */
public enum Action {
    ALLOW("允许"),
    BLOCK("阻止"),
    FORCE_SELL("强制卖出"),
    WARN("警告");

    private final String description;

    Action(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
