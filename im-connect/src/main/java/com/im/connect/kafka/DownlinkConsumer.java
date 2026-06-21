package com.im.connect.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.im.common.constant.KafkaTopics;
import com.im.common.dto.MessageEvent;
import com.im.connect.netty.ChannelManager;
import com.im.connect.route.RouteRegistry;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 消费下行事件，把消息推给本实例上在线的接收方（多端：推给该用户全部连接）。
 * 每个网关实例用独立消费组（广播），各自只投递本地持有的连接。
 * 全局离线（路由表为空）时发离线待推事件给 im-push（P1 骨架）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownlinkConsumer {

    private final ChannelManager channelManager;
    private final RouteRegistry routeRegistry;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.MESSAGE_DOWNLINK, groupId = "${im.gateway.id}")
    public void onMessage(String payload) {
        try {
            MessageEvent event = objectMapper.readValue(payload, MessageEvent.class);
            deliver(event, payload);
        } catch (Exception e) {
            log.error("handle downlink failed: {}", payload, e);
        }
    }

    private void deliver(MessageEvent event, String payload) {
        long userId = event.getTo();
        Collection<Channel> channels = channelManager.get(userId);

        boolean delivered = false;
        if (!channels.isEmpty()) {
            String text = toFrameText(event);
            for (Channel channel : channels) {
                if (channel != null && channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(text));
                    delivered = true;
                }
            }
        }

        // 本实例未投递且全局也不在线 -> 离线待推（骨架）。
        // 注：多实例广播下若真离线，各实例都会判定离线并各发一次，去重留待 P3。
        if (!delivered && !routeRegistry.isOnline(userId)) {
            try {
                kafkaTemplate.send(KafkaTopics.MESSAGE_OFFLINE, String.valueOf(userId), payload);
            } catch (Exception e) {
                log.warn("publish offline event failed for user {}: {}", userId, e.getMessage());
            }
        }
    }

    private String toFrameText(MessageEvent event) {
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
            return objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            throw new IllegalStateException("serialize downlink frame failed", e);
        }
    }
}
