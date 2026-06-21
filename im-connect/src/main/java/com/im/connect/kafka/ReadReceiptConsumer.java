package com.im.connect.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.im.common.constant.KafkaTopics;
import com.im.common.dto.ReadReceiptEvent;
import com.im.connect.netty.ChannelManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 消费已读回执事件，把"对端已读到 seq=X"反向推给本实例上在线的发送方（多端全推）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadReceiptConsumer {

    private final ChannelManager channelManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.MESSAGE_READ, groupId = "${im.gateway.id}-read")
    public void onRead(String payload) {
        try {
            ReadReceiptEvent event = objectMapper.readValue(payload, ReadReceiptEvent.class);
            Collection<Channel> channels = channelManager.get(event.getPeer());
            if (channels.isEmpty()) {
                return;
            }
            ObjectNode frame = objectMapper.createObjectNode();
            frame.put("type", "READ");
            frame.put("conversationId", event.getConversationId());
            frame.put("reader", event.getReader());
            frame.put("lastReadSeq", event.getLastReadSeq());
            String text = objectMapper.writeValueAsString(frame);
            for (Channel channel : channels) {
                if (channel != null && channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(text));
                }
            }
        } catch (Exception e) {
            log.error("handle read receipt failed: {}", payload, e);
        }
    }
}
