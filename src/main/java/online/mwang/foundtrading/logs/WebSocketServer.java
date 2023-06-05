package online.mwang.foundtrading.logs;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@Slf4j
@Component
@ServerEndpoint("/webSocket")
public class WebSocketServer {

    private static ApplicationContext applicationContext;

    private String sessionId;

    //解决无法注入mapper问题
    public static void setApplicationContext(ApplicationContext applicationContext) {
        WebSocketServer.applicationContext = applicationContext;
    }

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    @SneakyThrows
    public void onOpen(Session session) {
        LogsAppender logsAppender = applicationContext.getBean(LogsAppender.class);
        long sessionId = System.currentTimeMillis();
        this.sessionId = String.valueOf(sessionId);
        logsAppender.sessions.put(this.sessionId, session);
        log.info("建立连接");
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        LogsAppender logsAppender = applicationContext.getBean(LogsAppender.class);
        logsAppender.sessions.remove(this.sessionId);
        log.info("连接关闭");
    }
}