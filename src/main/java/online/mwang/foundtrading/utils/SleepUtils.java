package online.mwang.foundtrading.utils;

import lombok.SneakyThrows;

import java.util.concurrent.TimeUnit;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/1 16:57
 * @description: SleepUtils
 */
public class SleepUtils {

    @SneakyThrows
    public static void minutes(long minutes) {
        TimeUnit.MINUTES.sleep(minutes);
    }

    @SneakyThrows
    public static void second(long seconds) {
        TimeUnit.SECONDS.sleep(seconds);
    }
}
