// AI_GENERATE_START-----
package com.stock.execution.service;

import com.stock.execution.dto.SyncWindow;
import com.stock.execution.dto.SyncWindowResult;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 统一执行同步窗口，收敛成功、跳过和失败的控制流。
 * 领域服务只提供窗口动作和批次状态回调。
 *
 * @author mwangli
 * @since 2026-10-16
 */
@Component
public class SyncWindowExecutor {

    /**
     * 执行一个同步窗口并调用对应的终态回调。
     *
     * @param window 同步窗口
     * @param skipped 是否因已有成功记录而跳过
     * @param action 窗口业务动作
     * @param onSuccess 成功回调
     * @param onFailure 失败回调
     * @param <T> 业务结果类型
     * @return 窗口执行结果
     */
    public <T> SyncWindowResult<T> execute(
            SyncWindow window,
            boolean skipped,
            Supplier<T> action,
            Consumer<T> onSuccess,
            Consumer<RuntimeException> onFailure) {
        if (skipped) {
            return SyncWindowResult.skipped();
        }
        try {
            T value = action.get();
            onSuccess.accept(value);
            return SyncWindowResult.success(value);
        } catch (RuntimeException exception) {
            onFailure.accept(exception);
            return SyncWindowResult.failed(exception);
        }
    }
}
// AI_GENERATE_END-----
