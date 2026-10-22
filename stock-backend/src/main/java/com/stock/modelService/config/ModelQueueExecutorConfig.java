// AI_GENERATE_START -
package com.stock.modelService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 模型预测与评估持久化队列执行器配置。
 * 两类队列分别单线程有序消费，并允许不同批次的预测与评估并行执行。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Configuration
public class ModelQueueExecutorConfig {

    /**
     * 创建模型预测队列执行器。
     *
     * @return 单线程模型预测队列执行器
     */
    @Bean(name = "modelPredictionQueueExecutor")
    public Executor modelPredictionQueueExecutor() {
        return createSingleWorkerExecutor("model-prediction-queue-");
    }

    /**
     * 创建模型评估队列执行器。
     *
     * @return 单线程模型评估队列执行器
     */
    @Bean(name = "modelEvaluationQueueExecutor")
    public Executor modelEvaluationQueueExecutor() {
        return createSingleWorkerExecutor("model-evaluation-queue-");
    }

    private Executor createSingleWorkerExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
// AI_GENERATE_END -
