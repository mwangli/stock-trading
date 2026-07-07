package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动登录响应 DTO
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoLoginResponseDto {

    /**
     * 当前操作是否成功，状态接口中表示是否已登录。
     */
    private boolean success;

    /**
     * 当前结果提示信息。
     */
    private String message;

    /**
     * 已获取到的交易登录 Token；未登录或暂未解析到时为空。
     */
    private String token;

    /**
     * 当前浏览器页面阶段，如 ACTIVE_PHONE、LOGIN_FORM、AUTHENTICATED。
     */
    private String stage;

    /**
     * 当前浏览器 URL。
     */
    private String currentUrl;

    /**
     * 当前页面标题。
     */
    private String pageTitle;

    /**
     * 下一步建议动作。
     */
    private String nextAction;

    /**
     * 短信验证码文件路径。
     */
    private String smsCodeFile;

    /**
     * 图片验证码文件路径。
     */
    private String captchaCodeFile;

    /**
     * Docker Chrome noVNC 访问地址，人工处理滑块时使用。
     */
    private String noVncUrl;
}
