// AI_GENERATE_START------
package com.stock.trading.logging;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * WebSocket 日志 Appender 生命周期管理器。
 * 在 Spring 容器就绪后使用同一类加载器创建 Appender，并在容器关闭时解除注册。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Component
@RequiredArgsConstructor
public class WebSocketLogAppenderLifecycle implements SmartLifecycle {

    private static final String APPENDER_NAME = "ASYNC_WEBSOCKET_RUNTIME";
    private static final int QUEUE_SIZE = 512;

    private final LogBroadcaster logBroadcaster;

    private volatile boolean running;
    private AsyncAppender asyncAppender;

    /**
     * 将 WebSocket Appender 注册到 Logback 根日志器。
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> existingAppender = rootLogger.getAppender(APPENDER_NAME);
        if (existingAppender != null) {
            rootLogger.detachAppender(existingAppender);
            existingAppender.stop();
        }

        WebSocketAppender webSocketAppender = new WebSocketAppender(logBroadcaster);
        webSocketAppender.setContext(loggerContext);
        webSocketAppender.setName("WEBSOCKET_RUNTIME");

        ThresholdFilter thresholdFilter = new ThresholdFilter();
        thresholdFilter.setLevel(Level.INFO.levelStr);
        thresholdFilter.start();
        webSocketAppender.addFilter(thresholdFilter);
        webSocketAppender.start();

        asyncAppender = new AsyncAppender();
        asyncAppender.setContext(loggerContext);
        asyncAppender.setName(APPENDER_NAME);
        asyncAppender.setQueueSize(QUEUE_SIZE);
        asyncAppender.setDiscardingThreshold(0);
        asyncAppender.addAppender(webSocketAppender);
        asyncAppender.start();
        rootLogger.addAppender(asyncAppender);
        running = true;
    }

    /**
     * 从 Logback 根日志器移除 WebSocket Appender。
     */
    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(APPENDER_NAME);
        if (asyncAppender != null) {
            asyncAppender.stop();
            asyncAppender = null;
        }
        running = false;
    }

    /**
     * 返回当前 Appender 是否已注册。
     *
     * @return 已注册时返回 true
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 在普通业务生命周期组件之前启动日志桥接。
     *
     * @return 生命周期阶段
     */
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }
}
// AI_GENERATE_END------
