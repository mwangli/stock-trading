package online.mwang.stockTrading.core.listener;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Quartz 任务监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzJobListener implements ApplicationListener<ApplicationReadyEvent> {

    private final Scheduler scheduler;

    @Override
    @SneakyThrows
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        log.info("应用启动完成，定时任务加载器已就绪");
    }
}
