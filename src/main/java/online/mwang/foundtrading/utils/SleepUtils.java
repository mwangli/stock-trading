package online.mwang.foundtrading.utils;

import lombok.SneakyThrows;
import online.mwang.foundtrading.bean.base.BusinessException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/1 16:57
 * @description: SleepUtils
 */
public class SleepUtils {

    public static boolean interrupted = false;

    public static void checkInterrupted() {
        if (interrupted) {
            CompletableFuture.runAsync(SleepUtils::resetInterrupted);
            throw new BusinessException("任务终止!");
        }
    }

    @SneakyThrows
    public static void minutes(long minutes) {
        for (int i = 0; i < minutes * 60; i++) {
            second(1);
        }
    }

    @SneakyThrows
    public static void second(long seconds) {
        TimeUnit.SECONDS.sleep(seconds);
        checkInterrupted();
    }

    @SneakyThrows
    private static void resetInterrupted() {
        TimeUnit.SECONDS.sleep(1);
        interrupted = false;
    }
}
