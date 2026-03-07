package com.stock.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.stock.handler.LogWebSocketHandler;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WebSocketAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        // Broadcast the formatted message via WebSocket
        // Using a simple format for the payload: "TIMESTAMP [THREAD] LEVEL LOGGER - MESSAGE"
        String formattedMessage = format(eventObject);
        LogWebSocketHandler.broadcast(formattedMessage);
    }

    private String format(ILoggingEvent event) {
        // Simple manual formatting
        StringBuilder sb = new StringBuilder();
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(event.getTimeStamp())));
        sb.append(" [").append(event.getThreadName()).append("] ");
        sb.append(event.getLevel().toString()).append(" ");
        sb.append(event.getLoggerName()).append(" - ");
        sb.append(event.getFormattedMessage());
        return sb.toString();
    }
}
