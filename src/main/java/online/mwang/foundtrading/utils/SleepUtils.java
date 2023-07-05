package online.mwang.foundtrading.utils;

import lombok.SneakyThrows;
import online.mwang.foundtrading.bean.base.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/1 16:57
 * @description: SleepUtils
 */
@Component
public class SleepUtils {

    @Resource
    StringRedisTemplate redisTemplate;

    @SneakyThrows
    public void minutes(long minutes, String runningId) {
        for (int i = 0; i < 60 * minutes; i++) {
            second(1, runningId);
        }
    }


    @SneakyThrows
    public void second(long seconds) {
        TimeUnit.SECONDS.sleep(seconds);
    }

    @SneakyThrows
    public void second(long seconds, String runningId) {
        TimeUnit.SECONDS.sleep(seconds);
        if (checkRunningId(runningId)) {
            throw new BusinessException("任务终止!");
        }
    }

    public boolean checkRunningId(String runningId) {
        return redisTemplate.opsForValue().get(runningId) == null;
    }
}
