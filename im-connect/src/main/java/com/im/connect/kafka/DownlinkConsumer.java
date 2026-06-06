package com.im.connect.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.im.common.constant.KafkaTopics;
import com.im.common.dto.MessageEvent;
import com.im.connect.netty.ChannelManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 消费下行事件，把消息推给本实例上在线的接收方。
 * 每个网关实例用独立消费组（广播），各自只投递本地持有的连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownlinkConsumer {

    private final ChannelManager channelManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.MESSAGE_DOWNLINK, groupId = "${im.gateway.id}")
    public void onMessage(String payload) {
        try {
            MessageEvent event = objectMapper.readValue(payload, MessageEvent.class);
            deliver(event.getTo(), event);
        } catch (Exception e) {
            log.error("handle downlink failed: {}", payload, e);
        }
    }

    private void deliver(long userId, MessageEvent event) {
        Channel channel = channelManager.get(userId);
        if (channel == null || !channel.isActive()) {
            // 接收方不在本实例（或离线）。P0 不做离线存储，留待 P1。
            return;
        }
        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("type", "MESSAGE");
        frame.put("msgId", event.getMsgId());
        frame.put("seq", event.getSeq());
        frame.put("conversationId", event.getConversationId());
        frame.put("from", event.getFrom());
        frame.put("to", event.getTo());
        frame.put("content", event.getContent());
        frame.put("createdAt", event.getCreatedAt());
        try {
            channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(frame)));
        } catch (Exception e) {
            log.error("push to user {} failed", userId, e);
        }
    }
}
