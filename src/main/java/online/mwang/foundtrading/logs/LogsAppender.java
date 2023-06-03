package online.mwang.foundtrading.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

@Slf4j
@Component
public class LogsAppender extends AppenderBase<ILoggingEvent> {

    private static final Integer MAX_LOGS_LIMIT = 50;
    public final LinkedList<String> logsBuffer = new LinkedList<>();
    public final HashMap<String, Session> sessions = new HashMap<>();

    /**
     * 添加日志
     */
    @Override
    @SneakyThrows
    protected void append(ILoggingEvent iLoggingEvent) {
        String logs = doLayout(iLoggingEvent);
        logsBuffer.add(logs);
        while (logsBuffer.size() > MAX_LOGS_LIMIT) {
            logsBuffer.removeFirst();
        }
        // 向所有的会话发送缓存日志
        for (Session session : sessions.values()) {
            session.getBasicRemote().sendText(String.join("\r\n", logsBuffer));
//            session.getBasicRemote().sendText(logs);
        }
    }

    /**
     * 格式化日志
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