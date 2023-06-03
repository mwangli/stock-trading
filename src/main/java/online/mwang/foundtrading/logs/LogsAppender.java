package online.mwang.foundtrading.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

@Component
public class LogsAppender extends AppenderBase<ILoggingEvent> {

    @Resource
    private final WebSocketServer webSocketServer;
    private final Integer LOG_BUFFER_SIZE = 100;
    private LinkedList<String> logsBuffer = new LinkedList<>();

    public LogsAppender(WebSocketServer webSocketServer) {
        this.webSocketServer = webSocketServer;
    }

    /**
     * 添加日志
     *
     * @param iLoggingEvent
     */
    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        String logs = doLayout(iLoggingEvent);
        logsBuffer.add(logs);
        if (logsBuffer.size() >= LOG_BUFFER_SIZE) {
            logsBuffer.removeFirst();
        }
        webSocketServer.sendMessage(String.join("\r\n", logsBuffer));
    }

    /**
     * 格式化日志
     *
     * @param event
     * @return
     */
    public String doLayout(ILoggingEvent event) {
        StringBuilder sbuf = new StringBuilder();
        if (null != event && null != event.getMDCPropertyMap()) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");

            sbuf.append(simpleDateFormat.format(new Date(event.getTimeStamp())));
            sbuf.append("\t");

            sbuf.append(event.getLevel());
            sbuf.append("\t");

            sbuf.append(event.getThreadName());
            sbuf.append("\t");

            sbuf.append(event.getLoggerName());
            sbuf.append("\t");

            sbuf.append(event.getFormattedMessage().replace("\"", "\\\""));
            sbuf.append("\t");
        }

        return sbuf.toString();
    }
}