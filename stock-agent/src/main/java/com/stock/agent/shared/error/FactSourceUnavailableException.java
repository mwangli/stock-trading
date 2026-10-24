// AI_GENERATE_START --
package com.stock.agent.shared.error;

/**
 * 权威事实源不可用异常。
 *
 * 用于明确区分 Backend 事实源不可用与 Agent 自身运行失败，禁止调用方使用空对象或模型猜测降级。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public class FactSourceUnavailableException extends RuntimeException {

    /** 统一事实源不可用错误码。 */
    public static final String ERROR_CODE = "FACT_SOURCE_UNAVAILABLE";

    /**
     * 创建事实源不可用异常。
     *
     * @param message 不包含敏感信息的错误说明
     */
    public FactSourceUnavailableException(String message) {
        super(message);
    }

    /**
     * 创建带原始原因的事实源不可用异常。
     *
     * @param message 不包含敏感信息的错误说明
     * @param cause 原始异常
     */
    public FactSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 返回稳定错误码。
     *
     * @return FACT_SOURCE_UNAVAILABLE
     */
    public String getErrorCode() {
        return ERROR_CODE;
    }
}
// AI_GENERATE_END -
