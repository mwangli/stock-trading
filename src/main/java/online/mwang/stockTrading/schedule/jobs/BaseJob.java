package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.QuartzJob;
import online.mwang.stockTrading.web.logs.WebSocketServer;
import online.mwang.stockTrading.web.mapper.QuartzJobMapper;
import online.mwang.stockTrading.web.utils.DateUtils;
import online.mwang.stockTrading.web.utils.SleepUtils;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.websocket.Session;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/30 09:16
 * @description: CommomJob
 */
@Slf4j
@Component
public abstract class BaseJob implements InterruptableJob {

    public static int cores = Runtime.getRuntime().availableProcessors();
    public static int threads = (cores >> 1) + 1;
    public static ExecutorService fixedThreadPool = Executors.newFixedThreadPool(threads);
    public static ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

    public static final String VALIDATION_COLLECTION_NAME = "stockPredictPrice";
    public static final String TRAIN_COLLECTION_NAME = "stockHistoryPrice";
    public static final String TEST_COLLECTION_NAME = "stockTestPrice";
    public static final int EXAMPLE_LENGTH = 22;

    @Resource
    protected SleepUtils sleepUtils;
    @Resource
    protected MongoTemplate mongoTemplate;

    @Resource
    private QuartzJobMapper jobMapper;
    @Value("${profile:dev}")
    private String profile;

    public boolean debug = false;

    abstract void run();

    @SneakyThrows
    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        debug = "dev".equalsIgnoreCase(profile);
        final String jobName = jobExecutionContext.getJobDetail().getKey().getName();
        log.info("{}, 任务执行开始====================================", jobName);
        final long start = System.currentTimeMillis();
        setRunningStatus(jobName, "1");
        try {
            run();
        } catch (Exception e) {
            e.printStackTrace();
            log.info("任务执行出错：{}", e.getMessage());
        }
        setRunningStatus(jobName, "0");
        final long end = System.currentTimeMillis();
        log.info("{} ,任务执行结束====================================", jobName);
        log.info("{}, 任务执行耗时{}。", jobName, DateUtils.timeConvertor(end - start));
        // 通知所有的客户端会话，任务执行完成
        for (Session s : WebSocketServer.sessions) s.getBasicRemote().sendText("任务执行完成");
    }

    @Override
    public void interrupt() {
        log.info("正在尝试终止任务...");
    }

    private void setRunningStatus(String jobName, String running) {
        final LambdaQueryWrapper<QuartzJob> queryWrapper = new LambdaQueryWrapper<QuartzJob>().eq(QuartzJob::getName, jobName);
        final QuartzJob quartzJob = jobMapper.selectOne(queryWrapper);
        quartzJob.setRunning(running);
        jobMapper.updateById(quartzJob);
    }
}
