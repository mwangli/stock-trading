package com.stock.autoLogin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码识别测试用例定义
 *
 * @author mwangli
 * @since 2026-03-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaTestCase {

    /**
     * 测试用例ID
     */
    private String caseId;

    /**
     * 测试用例描述
     */
    private String description;

    /**
     * 预期答案（人工标注的正确结果）
     */
    private String expectedAnswer;

    /**
     * 难度等级（low/medium/high）
     */
    private String difficulty;

    /**
     * 干扰程度（low/medium/high）
     */
    private String interferenceLevel;

    /**
     * 创建预定义测试用例
     */
    public static CaptchaTestCase of(String caseId, String description,
            String expectedAnswer, String difficulty, String interferenceLevel) {
        return new CaptchaTestCase(caseId, description, expectedAnswer, difficulty, interferenceLevel);
    }

    /**
     * 获取预定义测试用例集
     */
    public static java.util.List<CaptchaTestCase> getPredefinedCases() {
        java.util.List<CaptchaTestCase> cases = new java.util.ArrayList<>();
        cases.add(of("TC001", "两位数加法（如 23+45）", "", "low", "low"));
        cases.add(of("TC002", "两位数减法（如 67-32）", "", "low", "low"));
        cases.add(of("TC003", "两位数混合运算（如 85-29）", "", "medium", "low"));
        cases.add(of("TC004", "三位数加法（如 123+456）", "", "medium", "medium"));
        cases.add(of("TC005", "三位数减法（如 789-234）", "", "medium", "medium"));
        cases.add(of("TC006", "三位数混合运算（如 456+123）", "", "high", "high"));
        cases.add(of("TC007", "结果为负数（如 23-85）", "", "medium", "medium"));
        cases.add(of("TC008", "大数字运算（如 999+999）", "", "high", "high"));
        return cases;
    }
}
