package online.mwang.stockTrading.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.Session;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author 13255
 */
@Slf4j
@RequiredArgsConstructor
public class LogsAppender extends AppenderBase<ILoggingEvent> {

    public final Session session;

    /**
     * 添加日志
     */
    @Override
    @SneakyThrows
    protected void append(ILoggingEvent iLoggingEvent) {
        session.getBasicRemote().sendText(doLayout(iLoggingEvent));
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
//            sbuf.append(event.getThreadName());
//            sbuf.append("\t");
//            sbuf.append(event.getLoggerName());
//            sbuf.append("\t");
            sbuf.append(event.getFormattedMessage().replace("\"", "\\\""));
            sbuf.append("\t");
        }
        return sbuf.toString();
    }
}