package com.stock.autoLogin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 验证码识别测试执行请求
 *
 * @author mwangli
 * @since 2026-03-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaTestRunRequest {

    /**
     * 资金账号/手机号
     */
    private String account;

    /**
     * 指定执行的测试用例ID列表（为空则执行全部）
     */
    private List<String> caseIds;

    /**
     * 是否保存截图
     */
    private boolean saveScreenshots;

    /**
     * 创建默认请求
     */
    public static CaptchaTestRunRequest defaultRequest(String account) {
        CaptchaTestRunRequest request = new CaptchaTestRunRequest();
        request.setAccount(account);
        request.setSaveScreenshots(true);
        return request;
    }
}
