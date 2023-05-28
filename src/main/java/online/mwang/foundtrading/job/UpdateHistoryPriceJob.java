package online.mwang.foundtrading.job;

import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdateHistoryPriceJob implements Job {

    private final DailyJob job;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        job.updateHistoryPrice();
    }
}
