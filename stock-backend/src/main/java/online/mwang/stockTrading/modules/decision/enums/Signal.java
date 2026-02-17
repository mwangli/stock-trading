package online.mwang.stockTrading.modules.decision.enums;

/**
 * 交易信号类型
 */
public enum Signal {
    BUY("买入"),
    SELL("卖出"),
    HOLD("观望");

    private final String description;

    Signal(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
