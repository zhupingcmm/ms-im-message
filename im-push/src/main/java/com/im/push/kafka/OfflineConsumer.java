package com.im.push.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.constant.KafkaTopics;
import com.im.common.dto.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 离线待推事件消费者（P1 骨架）。
 * 真实推送通道（APNs/FCM/厂商）待移动端接入后实现；本期仅记录「应推送」意图。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.MESSAGE_OFFLINE, groupId = "im-push")
    public void onOffline(String payload) {
        try {
            MessageEvent event = objectMapper.readValue(payload, MessageEvent.class);
            // TODO(P1+): 查询接收方推送 token，按设备类型走 APNs/FCM/厂商通道；更新角标未读数。
            log.info("[offline-push skeleton] would push msg {} to user {} (conv {})",
                    event.getMsgId(), event.getTo(), event.getConversationId());
        } catch (Exception e) {
            log.error("handle offline event failed: {}", payload, e);
        }
    }
}
