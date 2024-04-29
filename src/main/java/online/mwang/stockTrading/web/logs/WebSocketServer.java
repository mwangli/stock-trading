package online.mwang.stockTrading.web.logs;

import ch.qos.logback.classic.LoggerContext;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.jobs.RunBuyJob;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author 13255
 * WebSocketServer是多例的，每有一个客户端建立连接就有一个对应的WebSocketServer对象
 */
@Slf4j
@Component
@ServerEndpoint("/webSocket/{path}")
@RequiredArgsConstructor
public class WebSocketServer {

    public static List<Session> sessions = new ArrayList<>();
    private String sessionId;

    @OnOpen
    @SneakyThrows
    public void onOpen(Session session, @PathParam("path") String path) {
        if (path.equals("logs")) {
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
        if (path.equals("job")) {
            sessions.add(session);
            log.info("任务状态会话创建。");
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("path") String path) {
        if (path.equals("logs")) {
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger rootLogger = lc.getLogger("root");
            rootLogger.detachAppender("LogsAppender" + sessionId);
            log.info("日志采集器移除成功。");
        }
        if (path.equals("job")) {
            sessions.remove(session);
            log.info("任务状态会话移除。");
        }
    }
}