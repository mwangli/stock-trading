// AI_GENERATE_START------
package com.stock.trading.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Logback Appender，将日志通过 WebSocket 推送给前端
 * 由 Spring 生命周期组件创建并注入 {@link LogBroadcaster}，避免 Logback 与
 * Spring Boot DevTools 使用不同类加载器时无法取得真实广播服务。
 *
 * @author mwangli
 * @since 2026-03-11
 */
public class WebSocketAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final LogBroadcaster logBroadcaster;

    /**
     * 创建 WebSocket 日志 Appender。
     *
     * @param logBroadcaster 日志广播服务
     */
    public WebSocketAppender(LogBroadcaster logBroadcaster) {
        this.logBroadcaster = logBroadcaster;
    }

    /**
     * 将日志事件推送到 WebSocket 客户端
     *
     * @param eventObject 日志事件
     */
    @Override
    protected void append(ILoggingEvent eventObject) {
        // 广播服务自身的发送异常只写控制台，避免再次进入 WebSocket 形成递归日志。
        if (LogBroadcaster.class.getName().equals(eventObject.getLoggerName())) {
            return;
        }
        logBroadcaster.broadcast(format(eventObject));
    }

    /**
     * 将日志事件格式化为简洁的单行文本
     *
     * @param event 日志事件
     * @return 格式化后的日志文本
     */
    private String format(ILoggingEvent event) {
        StringBuilder message = new StringBuilder();
        message.append(DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        message.append(" [").append(event.getThreadName()).append("] ");
        message.append(String.format("%-5s", event.getLevel().toString())).append(" ");
        message.append(event.getLoggerName()).append(" - ");
        message.append(event.getFormattedMessage());
        if (event.getThrowableProxy() != null) {
            message.append(System.lineSeparator());
            message.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
        }
        return message.toString();
    }
}
// AI_GENERATE_END------
