package online.mwang.stockTrading.web.utils;

public class SleepUtils {
    
    public static void second(int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
    }
    
    public static void milliseconds(int millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
