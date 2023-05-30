package online.mwang.foundtrading.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Component
@RequiredArgsConstructor
public class RunNowJob extends BaseJob {

    private final DailyJob job;

    @Override
    public void run() {
        job.runNowJob();
    }
}
