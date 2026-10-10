// AI_GENERATE_START ---
package com.stock.tradingExecutor.execution;

/**
 * 算术图片验证码识别器。
 * 负责调用受控验证码识别能力并返回经过安全门禁校验的算术结果。
 *
 * @author mwangli
 * @since 2026-09-30
 */
public interface ArithmeticCaptchaSolver {

    /**
     * 识别并计算算术图片验证码。
     *
     * @param imageBytes 验证码图片原始字节
     * @return 安全门禁校验通过后的算术结果
     * @throws IllegalStateException 自动提交未启用、识别结果不一致或 OCR 服务不可用时抛出
     */
    int solve(byte[] imageBytes);
}
// AI_GENERATE_END ---