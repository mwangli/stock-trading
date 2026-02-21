package online.mwang.stockTrading.utils;

import org.springframework.stereotype.Component;

/**
 * 睡眠工具类
 */
@Component
public class SleepUtils {

    public static void second(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void milliseconds(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
