package online.mwang.foundtrading.logs;

import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.util.LinkedList;
import java.util.Random;

@Component
@ServerEndpoint("/webSocket")
public class WebSocketServer {

    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;

    private Integer sessionId;

    private final Integer LOG_BUFFER_SIZE = 80;
    private LinkedList<String> logsBuffer = new LinkedList<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.sessionId = (new Random()).nextInt(100000);
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // 第二步：获取日志对象 （日志是有继承关系的，关闭上层，下层如果没有特殊说明也会关闭）
        ch.qos.logback.classic.Logger rootLogger = lc.getLogger("root");
        LogsAppender logsAppender = new LogsAppender(this);
        logsAppender.setContext(lc);
        // 自定义Appender设置name
        logsAppender.setName("myAppender" + sessionId);
        logsAppender.start();
        rootLogger.addAppender(logsAppender);
        System.out.println("注入成功");
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = lc.getLogger("root");
        // 通过name移除Appender
        rootLogger.detachAppender("LogsAppender" + sessionId);
        System.out.println("--------移除成功--------");
    }

    /**
     * 服务器主动发送消息
     */
    @SneakyThrows
    public void sendMessage(String message) {
        logsBuffer.add(message);
        if (logsBuffer.size() >= LOG_BUFFER_SIZE) {
            logsBuffer.removeFirst();
        }
        if (session.isOpen()) {
            this.session.getBasicRemote().sendText(String.join("\r\n", logsBuffer));
        }
    }
}