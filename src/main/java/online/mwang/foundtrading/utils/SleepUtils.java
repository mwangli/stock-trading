package online.mwang.foundtrading.utils;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/1 16:57
 * @description: SleepUtils
 */
@Component
public class SleepUtils {

    @SneakyThrows
    public static void minutes(long minutes) {
        TimeUnit.SECONDS.sleep(minutes);
    }

    @SneakyThrows
    public static void second(long seconds) {

        TimeUnit.SECONDS.sleep(seconds);
    }

    @SneakyThrows
    public static void milliSecond(long milliSecond) {
        TimeUnit.MILLISECONDS.sleep(milliSecond);
    }
}
