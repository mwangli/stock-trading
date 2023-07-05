package online.mwang.foundtrading.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.utils.SleepUtils;
import org.springframework.stereotype.Component;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunTestJob extends BaseJob {

    private final SleepUtils sleepUtils;
    private static final int TRY_TIMES = 100;

    @Override
    public void run(String runningId) {
        int times = 0;
        while (times++ < TRY_TIMES) {
            log.info("执行测试任务......");
            sleepUtils.second(1, runningId);
        }
    }
}
