package online.mwang.foundtrading.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.po.QuartzJob;
import online.mwang.foundtrading.controller.JobController;
import online.mwang.foundtrading.mapper.QuartzJobMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzJobListener implements ApplicationListener<ApplicationReadyEvent> {

    private final QuartzJobMapper jobMapper;
    private final JobController jobController;

    @Override
    @SneakyThrows
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("开始加载Quartz定时任务.......");
        List<QuartzJob> jobs = jobMapper.selectList(new QueryWrapper<>());
        jobs.forEach(jobController::startJob);
        log.info("Quartz定时任务加载完毕.......");
    }
}
