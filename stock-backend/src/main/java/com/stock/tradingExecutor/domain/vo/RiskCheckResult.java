package com.stock.tradingExecutor.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 风控检查结果
 */
@Data
@Builder
public class RiskCheckResult {

    private boolean passed;
    private List<String> violations;
    private AccountStatus accountStatus;

    public static RiskCheckResult pass(AccountStatus accountStatus) {
        return RiskCheckResult.builder()
                .passed(true)
                .violations(List.of())
                .accountStatus(accountStatus)
                .build();
    }

    public static RiskCheckResult reject(List<String> violations, AccountStatus accountStatus) {
        return RiskCheckResult.builder()
                .passed(false)
                .violations(violations)
                .accountStatus(accountStatus)
                .build();
    }
}
