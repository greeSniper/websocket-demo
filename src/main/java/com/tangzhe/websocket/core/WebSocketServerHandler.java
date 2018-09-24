package com.tangzhe.websocket.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private static Map<String, BigDecimal> STOCK_MAP;

    static {
        STOCK_MAP = new HashMap<>();
        STOCK_MAP.put("1001", new BigDecimal("12.85"));
        STOCK_MAP.put("1002", new BigDecimal("8.23"));
        STOCK_MAP.put("1003", new BigDecimal("10.11"));
    }

    private WebSocketServerHandshaker handshaker;

    private String host;
    private Integer port;
    private String uri;

    public WebSocketServerHandler(String host, Integer port, String uri) {
        this.host = host;
        this.port = port;
        this.uri = uri;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // HTTP接入，webSocket第一次连接使用HTTP连接, 用于握手
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        }

        // webSocket接入
        else if (msg instanceof WebSocketFrame) {
            handlerWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess()
                || (!"websocket".equals(req.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        String webSocketURL = "ws://" + this.host + ":" + this.port + "/" + this.uri;
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                webSocketURL, null, false);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        // 返回应答给客户端
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(),
                    CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        // 如果是非Keep-Alive，关闭连接
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        return false;
    }

    private void handlerWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 判断是否关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }

        // 判断是否ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        // 本例程仅支持文本消息，不支持二进制消息
        if (frame instanceof BinaryWebSocketFrame) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
        }

        if (frame instanceof TextWebSocketFrame) {
            // 返回应答消息
            String request = ((TextWebSocketFrame) frame).text();
            log.info("服务端收到：" + request);
            String value = STOCK_MAP.get(request)!=null ? String.valueOf(STOCK_MAP.get(request)) : "停牌";
            String response = request + ": " + value;
            BigDecimal bigDecimal = STOCK_MAP.get(request);
            if (bigDecimal != null) {
                STOCK_MAP.put(request, bigDecimal.add(new BigDecimal(1)));
            } else {
                STOCK_MAP.put(request, new BigDecimal(1));
            }
            ctx.channel().write(new TextWebSocketFrame(response));
        }
    }

}
