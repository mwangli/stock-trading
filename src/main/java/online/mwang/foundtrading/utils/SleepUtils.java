package online.mwang.foundtrading.utils;

import lombok.SneakyThrows;
import online.mwang.foundtrading.bean.base.BusinessException;

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
            interrupted = false;
            throw new BusinessException("任务终止!");
        }
    }

    @SneakyThrows
    public static void minutes(long minutes) {
        TimeUnit.MINUTES.sleep(minutes);
        checkInterrupted();
    }

    @SneakyThrows
    public static void second(long seconds) {
        TimeUnit.SECONDS.sleep(seconds);
        checkInterrupted();
    }

    @SneakyThrows
    public static void milliSecond(long milliSecond) {
        TimeUnit.MILLISECONDS.sleep(milliSecond);
        checkInterrupted();
    }
}
