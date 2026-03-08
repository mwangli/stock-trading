package com.stock.tradingExecutor.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.stock.tradingExecutor.handler.LogWebSocketHandler;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logback Appender，将日志通过 WebSocket 推送给前端
 */
public class WebSocketAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        String formattedMessage = format(eventObject);
        LogWebSocketHandler.broadcast(formattedMessage);
    }

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
