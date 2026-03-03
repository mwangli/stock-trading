package com.stock.tradingExecutor.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 风控检查结果
 */
@Data
@Builder
public class RiskCheckResult {
    /**
     * 是否通过
     */
    private boolean passed;
    
    /**
     * 违规列表
     */
    private List<String> violations;
    
    /**
     * 账户状态
     */
    private AccountStatus accountStatus;
    
    /**
     * 创建通过结果
     */
    public static RiskCheckResult pass(AccountStatus accountStatus) {
        return RiskCheckResult.builder()
                .passed(true)
                .violations(List.of())
                .accountStatus(accountStatus)
                .build();
    }
    
    /**
     * 创建拒绝结果
     */
    public static RiskCheckResult reject(List<String> violations, AccountStatus accountStatus) {
        return RiskCheckResult.builder()
                .passed(false)
                .violations(violations)
                .accountStatus(accountStatus)
                .build();
    }
}