package online.mwang.foundtrading.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.utils.SleepUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

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

    private static final int TRY_TIMES = 100;

    @Resource
    private DailyJob job;

    @Override
    public void run() {
        int times = 0;
        while (!job.interrupted && times++ < TRY_TIMES) {
            SleepUtils.second(2);
            log.info("执行测试任务......");
        }
    }
}
