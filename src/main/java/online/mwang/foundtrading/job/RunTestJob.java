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

    private boolean flag = true;

    @Override
    public void run() {
        while (flag) {
            SleepUtils.second(2);
            log.info("执行测试任务......");
        }
    }

    @Override
    public void interrupt() {
        log.info("测试任务终止！");
        log.info(Thread.currentThread().getName());
        flag = false;
        Thread.currentThread().interrupt();
//        interrupt = true;
    }
}
