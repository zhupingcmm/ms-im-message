package com.im.connect.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.im.common.api.Result;
import com.im.common.dto.SendMessageRequest;
import com.im.common.dto.SendMessageResponse;
import com.im.common.jwt.JwtUtil;
import com.im.connect.client.MessageClient;
import com.im.connect.route.RouteRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket 业务帧处理器（无状态、Sharable，单例共享给所有连接）。
 * 协议（JSON 文本帧）：
 *   入站  LOGIN {token}        | LOGOUT {} | SEND {clientMsgId,to,content}
 *   出站  LOGIN_ACK {code,userId} | LOGOUT_ACK {code,userId} | SEND_ACK {code,clientMsgId,msgId,seq} | MESSAGE {...} | ERROR {code,msg}
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final JwtUtil jwtUtil;
    private final MessageClient messageClient;
    private final ChannelManager channelManager;
    private final RouteRegistry routeRegistry;
    private final ObjectMapper objectMapper;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        JsonNode node = objectMapper.readTree(frame.text());
        String type = node.path("type").asText();
        switch (type) {
            case "LOGIN" -> handleLogin(ctx, node);
            case "LOGOUT" -> handleLogout(ctx);
            case "SEND" -> handleSend(ctx, node);
            case "PING" -> handlePing(ctx);
            default -> sendError(ctx.channel(), 40000, "unknown type: " + type);
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, JsonNode node) {
        String token = node.path("token").asText();
        long userId;
        try {
            userId = jwtUtil.parseUserId(token);
        } catch (Exception e) {
            sendError(ctx.channel(), 40100, "invalid token");
            ctx.close();
            return;
        }
        // P1 多端：允许同账号多设备登录，按 deviceId 区分；同设备重连覆盖旧连接
        String deviceId = node.path("deviceId").asText("");
        if (deviceId.isEmpty()) {
            deviceId = ctx.channel().id().asShortText();
        }
        channelManager.bind(userId, deviceId, ctx.channel());
        routeRegistry.online(userId);
        ObjectNode ack = objectMapper.createObjectNode();
        ack.put("type", "LOGIN_ACK");
        ack.put("code", 0);
        ack.put("userId", userId);
        ack.put("deviceId", deviceId);
        send(ctx.channel(), ack);
        log.info("user {} logged in, device {}, channel {}", userId, deviceId, ctx.channel().id());
    }

    private void handleLogout(ChannelHandlerContext ctx) {
        Long userId = ctx.channel().attr(ChannelManager.USER_ID).get();
        if (userId == null) {
            sendError(ctx.channel(), 40101, "not logged in");
            return;
        }
        channelManager.unbind(ctx.channel());
        // 仅当该用户在本实例已无其它在线连接时，才从路由表摘除
        if (!channelManager.isOnline(userId)) {
            routeRegistry.offline(userId);
        }
        ObjectNode ack = objectMapper.createObjectNode();
        ack.put("type", "LOGOUT_ACK");
        ack.put("code", 0);
        ack.put("userId", userId);
        send(ctx.channel(), ack);
        log.info("user {} logged out, channel {}", userId, ctx.channel().id());
    }

    private void handleSend(ChannelHandlerContext ctx, JsonNode node) {
        Long from = ctx.channel().attr(ChannelManager.USER_ID).get();
        if (from == null) {
            sendError(ctx.channel(), 40101, "not logged in");
            return;
        }
        SendMessageRequest req = new SendMessageRequest();
        req.setClientMsgId(node.path("clientMsgId").asText());
        req.setFrom(from);
        req.setTo(node.path("to").asLong());
        req.setType(1);
        req.setContent(node.path("content").asText());

        try {
            Result<SendMessageResponse> result = messageClient.send(req);
            ObjectNode ack = objectMapper.createObjectNode();
            ack.put("type", "SEND_ACK");
            ack.put("code", result.getCode());
            ack.put("clientMsgId", req.getClientMsgId());
            if (result.isOk() && result.getData() != null) {
                SendMessageResponse data = result.getData();
                ack.put("msgId", data.getMsgId());
                ack.put("seq", data.getSeq());
                ack.put("createdAt", data.getCreatedAt());
            } else {
                ack.put("msg", result.getMsg());
            }
            send(ctx.channel(), ack);
        } catch (Exception e) {
            log.error("send message failed", e);
            sendError(ctx.channel(), 50000, "send failed");
        }
    }

    private void handlePing(ChannelHandlerContext ctx) {
        ObjectNode pong = objectMapper.createObjectNode();
        pong.put("type", "PONG");
        send(ctx.channel(), pong);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.READER_IDLE) {
            // 读空闲超时：判定为死连接，关闭后由 channelInactive 清理路由
            log.info("channel {} read idle, closing", ctx.channel().id());
            ctx.close();
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long userId = ctx.channel().attr(ChannelManager.USER_ID).get();
        channelManager.unbind(ctx.channel());
        if (userId != null) {
            // 多端：仅当本实例已无该用户其它连接时才摘除路由
            if (!channelManager.isOnline(userId)) {
                routeRegistry.offline(userId);
            }
            log.info("user {} disconnected, channel {}", userId, ctx.channel().id());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("channel error: {}", cause.getMessage());
        ctx.close();
    }

    private void sendError(Channel channel, int code, String msg) {
        ObjectNode err = objectMapper.createObjectNode();
        err.put("type", "ERROR");
        err.put("code", code);
        err.put("msg", msg);
        send(channel, err);
    }

    private void send(Channel channel, ObjectNode node) {
        try {
            channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(node)));
        } catch (Exception e) {
            log.error("write frame failed", e);
        }
    }
}
