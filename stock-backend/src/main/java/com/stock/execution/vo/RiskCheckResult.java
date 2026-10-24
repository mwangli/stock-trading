// AI_GENERATE_START------
package com.stock.execution.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 风控检查结果
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
public class RiskCheckResult {

    /**
     * 风控检查是否通过。
     */
    private boolean passed;
    /**
     * 未通过的风控规则明细列表。
     */
    private List<String> violations;
    /**
     * 执行风控检查时的账户状态快照。
     */
    private AccountStatus accountStatus;

    /**
     * 创建风控通过结果。
     *
     * @param accountStatus 风控校验时的账户状态
     * @return 风控通过结果
     */
    public static RiskCheckResult pass(AccountStatus accountStatus) {
        return RiskCheckResult.builder()
                .passed(true)
                .violations(List.of())
                .accountStatus(accountStatus)
                .build();
    }

    /**
     * 创建风控拒绝结果。
     *
     * @param violations 风控违规明细
     * @param accountStatus 风控校验时的账户状态
     * @return 风控拒绝结果
     */
    public static RiskCheckResult reject(List<String> violations, AccountStatus accountStatus) {
        return RiskCheckResult.builder()
                .passed(false)
                .violations(violations)
                .accountStatus(accountStatus)
                .build();
    }
}

// AI_GENERATE_END------
