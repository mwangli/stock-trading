package online.mwang.stockTrading.web.utils;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SleepUtils {

    @SneakyThrows
    public void milliseconds(long seconds) {
        TimeUnit.MILLISECONDS.sleep(seconds);
    }


    @SneakyThrows
    public void second(long seconds) {
        TimeUnit.SECONDS.sleep(seconds);
    }


    @SneakyThrows
    public void minutes(long seconds) {
        TimeUnit.MINUTES.sleep(seconds);
    }
}
