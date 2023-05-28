package online.mwang.foundtrading.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunTestJob implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        log.info("执行测试任务......");
    }
}
