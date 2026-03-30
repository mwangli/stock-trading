package com.stock.autoLogin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单个验证码测试用例执行结果
 *
 * @author mwangli
 * @since 2026-03-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaTestCaseResult {

    /**
     * 测试用例ID
     */
    private String caseId;

    /**
     * 识别的数学表达式（如 "39 + 85"）
     */
    private String expression;

    /**
     * 预期答案
     */
    private String expectedAnswer;

    /**
     * 实际识别结果
     */
    private String actualAnswer;

    /**
     * 是否通过
     */
    private boolean passed;

    /**
     * 识别方法（baidu_ocr、local_digit、image_analysis）
     */
    private String method;

    /**
     * 错误信息（识别失败时）
     */
    private String errorMessage;

    /**
     * 截图文件路径
     */
    private String screenshotPath;

    /**
     * 识别耗时（毫秒）
     */
    private long elapsedTimeMs;

    /**
     * 创建通过结果
     */
    public static CaptchaTestCaseResult pass(String caseId, String expression,
            String expectedAnswer, String actualAnswer, String method, long elapsedTimeMs) {
        CaptchaTestCaseResult result = new CaptchaTestCaseResult();
        result.setCaseId(caseId);
        result.setExpression(expression);
        result.setExpectedAnswer(expectedAnswer);
        result.setActualAnswer(actualAnswer);
        result.setPassed(true);
        result.setMethod(method);
        result.setElapsedTimeMs(elapsedTimeMs);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static CaptchaTestCaseResult fail(String caseId, String expectedAnswer,
            String errorMessage, long elapsedTimeMs) {
        CaptchaTestCaseResult result = new CaptchaTestCaseResult();
        result.setCaseId(caseId);
        result.setExpectedAnswer(expectedAnswer);
        result.setPassed(false);
        result.setErrorMessage(errorMessage);
        result.setElapsedTimeMs(elapsedTimeMs);
        return result;
    }
}
