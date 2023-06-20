package online.mwang.foundtrading.logs;

import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.util.UUID;

/**
 * @author 13255
 */
@Slf4j
@Component
@ServerEndpoint("/webSocket")
public class WebSocketServer {

    private String sessionId;

    @OnOpen
    @SneakyThrows
    public void onOpen(Session session) {
        sessionId = String.valueOf(UUID.randomUUID());
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = lc.getLogger("root");
        final LogsAppender logsAppender = new LogsAppender(session);
        logsAppender.setContext(lc);
        logsAppender.setName("LogsAppender" + sessionId);
        logsAppender.start();
        rootLogger.addAppender(logsAppender);
        log.info("日志采集器注入成功。");
    }

    @OnClose
    public void onClose() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = lc.getLogger("root");
        rootLogger.detachAppender("LogsAppender" + sessionId);
        System.out.println("日志采集器移除成功。");
    }
}