package com.im.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.constant.KafkaTopics;
import com.im.common.constant.RedisKeys;
import com.im.common.dto.MessageEvent;
import com.im.common.dto.SendMessageRequest;
import com.im.common.dto.SendMessageResponse;
import com.im.common.id.Snowflake;
import com.im.common.util.ConversationIdUtil;
import com.im.message.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessagePersister persister;
    private final StringRedisTemplate redis;
    private final Snowflake snowflake;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 单聊发送：幂等 -> 生成序号 -> 事务落库(message + 双方 inbox + 会话) -> 发 Kafka 下行。
     */
    public SendMessageResponse send(SendMessageRequest req) {
        // 1. 幂等：clientMsgId 命中则直接返回已存结果
        String idempKey = RedisKeys.idemp(req.getClientMsgId());
        String cached = redis.opsForValue().get(idempKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, SendMessageResponse.class);
            } catch (Exception ignore) {
                // 解析失败则继续重新处理
            }
        }

        long from = req.getFrom();
        long to = req.getTo();
        String conversationId = ConversationIdUtil.single(from, to);

        // 2. 会话内序号
        Long seq = redis.opsForValue().increment(RedisKeys.convSeq(conversationId));

        // 3. 生成 msgId 并组装消息
        long msgId = snowflake.nextId();
        LocalDateTime now = LocalDateTime.now();
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setMsgId(msgId);
        msg.setSeq(seq);
        msg.setSenderId(from);
        msg.setType(req.getType() == null ? 1 : req.getType());
        msg.setContent(req.getContent());
        msg.setStatus(1);
        msg.setCreatedAt(now);

        // 3.1 事务落库：message 正文 + 收发双方 inbox（写扩散）+ 会话摘要
        String preview = req.getContent() == null ? ""
                : (req.getContent().length() > 255 ? req.getContent().substring(0, 255) : req.getContent());
        persister.persist(msg, from, to, conversationId, preview, now);

        long createdAt = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        SendMessageResponse resp = new SendMessageResponse();
        resp.setClientMsgId(req.getClientMsgId());
        resp.setMsgId(msgId);
        resp.setSeq(seq);
        resp.setConversationId(conversationId);
        resp.setCreatedAt(createdAt);

        // 4. 写幂等缓存
        try {
            redis.opsForValue().set(idempKey, objectMapper.writeValueAsString(resp), Duration.ofDays(1));
        } catch (Exception e) {
            log.warn("write idemp cache failed: {}", e.getMessage());
        }

        // 5. 发 Kafka 下行事件
        MessageEvent event = new MessageEvent();
        event.setMsgId(msgId);
        event.setSeq(seq);
        event.setConversationId(conversationId);
        event.setFrom(from);
        event.setTo(to);
        event.setType(msg.getType());
        event.setContent(msg.getContent());
        event.setCreatedAt(createdAt);
        try {
            kafkaTemplate.send(KafkaTopics.MESSAGE_DOWNLINK, String.valueOf(to),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("publish downlink failed", e);
        }

        return resp;
    }
}
