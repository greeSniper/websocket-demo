package com.tangzhe.websocket.core;

import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/myWebSocket/{userId}")
@Component
public class WebComment {

    private static int onlineCount = 0;

    private static ConcurrentHashMap<String, Session> webSocketMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        webSocketMap.put(userId, session);
        addOnlineCount();
        System.out.println(userId + "进入聊天室");
        System.out.println("有新连接加入！当前在线人数为" + getOnlineCount());
    }

    @OnClose
    public void onClose(Session session) {
        Map<String, String> map = session.getPathParameters();
        webSocketMap.remove(map.get("userId"));
        for (String user : webSocketMap.keySet()) {
            System.out.println(user);
        }
        subOnlineCount();
        System.out.println("有一连接关闭！当前在线人数为" + getOnlineCount());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("来自客户端的消息:" + message);
        Map<String, String> map = session.getPathParameters();
        String userId = map.get("userId");
        for (String user : webSocketMap.keySet()) {
            try {
                sendMessage(user + "你好，我是" + userId + "   " + message, webSocketMap.get(user));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.out.println("发生错误");
        error.printStackTrace();
    }

    public void sendMessage(String message, Session session) throws IOException {
        if (session.isOpen()) {
            session.getAsyncRemote().sendText(message);
        }
        //this.session.getAsyncRemote().sendText(message);
    }

    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        WebComment.onlineCount ++;
    }

    public static synchronized void subOnlineCount() {
        WebComment.onlineCount --;
    }

}