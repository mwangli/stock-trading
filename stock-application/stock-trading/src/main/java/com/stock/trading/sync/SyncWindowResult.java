// AI_GENERATE_START---
package com.stock.trading.sync;

/**
 * 单个同步窗口的执行结果。
 *
 * @param status 窗口状态
 * @param value 领域处理结果
 * @param error 失败异常
 * @param <T> 领域处理结果类型
 * @author mwangli
 * @since 2026-10-16
 */
public record SyncWindowResult<T>(Status status, T value, RuntimeException error) {

    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILED
    }

    /** 创建成功结果。 */
    public static <T> SyncWindowResult<T> success(T value) {
        return new SyncWindowResult<>(Status.SUCCESS, value, null);
    }

    /** 创建跳过结果。 */
    public static <T> SyncWindowResult<T> skipped() {
        return new SyncWindowResult<>(Status.SKIPPED, null, null);
    }

    /** 创建失败结果。 */
    public static <T> SyncWindowResult<T> failed(RuntimeException error) {
        return new SyncWindowResult<>(Status.FAILED, null, error);
    }
}
// AI_GENERATE_END---
