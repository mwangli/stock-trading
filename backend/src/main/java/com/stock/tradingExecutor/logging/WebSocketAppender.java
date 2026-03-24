package com.stock.tradingExecutor.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logback Appender，将日志通过 WebSocket 推送给前端
 * 通过 {@link ApplicationContextHolder} 获取 Spring 管理的 {@link LogBroadcastService}，
 * 避免直接依赖 WebSocket Handler 的静态字段，解决 devtools 场景下类加载器隔离问题。
 *
 * @author mwangli
 * @since 2026-03-11
 */
public class WebSocketAppender extends AppenderBase<ILoggingEvent> {

    /**
     * 将日志事件推送到 WebSocket 客户端
     *
     * @param eventObject 日志事件
     */
    @Override
    protected void append(ILoggingEvent eventObject) {
        String formattedMessage = format(eventObject);
        try {
            LogBroadcastService logBroadcastService = ApplicationContextHolder.getBean(LogBroadcastService.class);
            logBroadcastService.broadcast(formattedMessage);
        } catch (IllegalStateException ex) {
            // 应用上下文尚未就绪时静默跳过，避免重复打印（CONSOLE appender 已输出）
        }
    }

    /**
     * 将日志事件格式化为简洁的单行文本
     *
     * @param event 日志事件
     * @return 格式化后的日志文本
     */
    private String format(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(event.getTimeStamp())));
        sb.append(" [").append(event.getThreadName()).append("] ");
        sb.append(event.getLevel().toString()).append(" ");
        sb.append(event.getLoggerName()).append(" - ");
        sb.append(event.getFormattedMessage());
        return sb.toString();
    }
}
