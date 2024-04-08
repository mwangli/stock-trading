package online.mwang.stockTrading.schedule.jobs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/8 17:01
 * @description: RunDataCleanJob
 */
@Slf4j
@Component
public class RunCleanJob extends BaseJob {

    @Override
    void run() {
        // 清理系统运行过程中留下的无用数据，或者补充缺失数据
        log.info("正在清理系统中的无用数据...");
    }
}
