package online.mwang.stockTrading.web.job;

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

    private final AllJobs jobs;

    @Override
    public void run(String runningId) {
        jobs.runNowJob();
    }
}