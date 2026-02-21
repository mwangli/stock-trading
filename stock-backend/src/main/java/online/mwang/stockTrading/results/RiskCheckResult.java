package online.mwang.stockTrading.results;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.mwang.stockTrading.enums.Action;

/**
 * 风控检查结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskCheckResult {

    /**
     * 是否通过检查
     */
    private boolean passed;

    /**
     * 消息
     */
    private String message;

    /**
     * 触发的动作
     */
    private Action action;

    /**
     * 风控等级
     */
    private String riskLevel;

    public static RiskCheckResult pass() {
        return RiskCheckResult.builder()
                .passed(true)
                .message("风控检查通过")
                .action(Action.ALLOW)
                .build();
    }

    public static RiskCheckResult fail(String message) {
        return RiskCheckResult.builder()
                .passed(false)
                .message(message)
                .action(Action.BLOCK)
                .build();
    }

    public static RiskCheckResult forceSell(String message) {
        return RiskCheckResult.builder()
                .passed(false)
                .message(message)
                .action(Action.FORCE_SELL)
                .build();
    }

    public boolean isBlocked() {
        return !passed && Action.BLOCK.equals(action);
    }

    public boolean shouldForceSell() {
        return Action.FORCE_SELL.equals(action);
    }
}
