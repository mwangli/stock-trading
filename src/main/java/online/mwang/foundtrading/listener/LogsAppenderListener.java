package online.mwang.foundtrading.listener;

import ch.qos.logback.classic.LoggerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.logs.LogsAppender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogsAppenderListener implements ApplicationListener<ApplicationReadyEvent> {

    private final LogsAppender logsAppender;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        registerLogsAppender();
    }

    private void registerLogsAppender() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = lc.getLogger("root");
        logsAppender.setContext(lc);
        logsAppender.setName("LogsAppender");
        logsAppender.start();
        rootLogger.addAppender(logsAppender);
        log.info("日志采集器注入完成。");
    }
}
