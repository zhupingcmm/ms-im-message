package com.im.message.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.constant.KafkaTopics;
import com.im.common.dto.ReadReceiptEvent;
import com.im.common.util.ConversationIdUtil;
import com.im.message.dto.ConversationView;
import com.im.message.dto.HistoryResponse;
import com.im.message.dto.MessageView;
import com.im.message.entity.Conversation;
import com.im.message.entity.Message;
import com.im.message.mapper.ConversationMapper;
import com.im.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话/历史读取服务（登录后拉列表、进会话拉历史）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** A 登录后的会话列表，按最后消息时间倒序。 */
    public List<ConversationView> listConversations(long userId) {
        List<Conversation> rows = conversationMapper.selectList(
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getOwnerId, userId)
                        .orderByDesc(Conversation::getLastMsgTime));
        List<ConversationView> views = new ArrayList<>(rows.size());
        for (Conversation c : rows) {
            ConversationView v = new ConversationView();
            v.setPeerId(c.getPeerId());
            v.setConversationId(c.getConversationId());
            v.setLastMsgId(c.getLastMsgId());
            v.setLastSeq(c.getLastSeq());
            v.setLastContent(c.getLastContent());
            v.setLastMsgTime(toEpochMilli(c.getLastMsgTime()));
            v.setUnreadCount(c.getUnreadCount());
            views.add(v);
        }
        return views;
    }

    /**
     * A 与 peer 的历史消息，按 seq 倒序游标分页，返回时转为升序（旧->新）。
     * beforeSeq 为 null 取最新一页；否则取 seq < beforeSeq 的更旧消息。
     */
    public HistoryResponse history(long userId, long peerId, Long beforeSeq, int limit) {
        int size = (limit <= 0 || limit > MAX_LIMIT) ? DEFAULT_LIMIT : limit;
        String conversationId = ConversationIdUtil.single(userId, peerId);

        List<Message> rows = messageMapper.selectList(
                Wrappers.<Message>lambdaQuery()
                        .eq(Message::getConversationId, conversationId)
                        .lt(beforeSeq != null, Message::getSeq, beforeSeq)
                        .orderByDesc(Message::getSeq)
                        .last("limit " + size));

        boolean hasMore = rows.size() == size;
        Long nextCursor = rows.isEmpty() ? null : rows.get(rows.size() - 1).getSeq();

        List<MessageView> messages = new ArrayList<>(rows.size());
        for (Message m : rows) {
            MessageView v = new MessageView();
            v.setMsgId(m.getMsgId());
            v.setSeq(m.getSeq());
            v.setSenderId(m.getSenderId());
            v.setType(m.getType());
            v.setContent(m.getContent());
            v.setCreatedAt(toEpochMilli(m.getCreatedAt()));
            v.setStatus(m.getStatus());
            messages.add(v);
        }
        Collections.reverse(messages); // 倒序查出 -> 翻转为升序展示

        HistoryResponse resp = new HistoryResponse();
        resp.setConversationId(conversationId);
        resp.setMessages(messages);
        resp.setNextCursor(hasMore ? nextCursor : null);
        resp.setHasMore(hasMore);
        return resp;
    }

    /** 进入会话后清零未读、推进已读位点，并向对端发送方发已读回执。 */
    public void markRead(long userId, long peerId) {
        conversationMapper.markRead(userId, peerId);

        // 读出推进后的已读位点，反向通知对端（消息发送方）
        Conversation row = conversationMapper.selectOne(
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getOwnerId, userId)
                        .eq(Conversation::getPeerId, peerId));
        if (row == null) {
            return;
        }
        ReadReceiptEvent event = new ReadReceiptEvent();
        event.setConversationId(row.getConversationId());
        event.setReader(userId);
        event.setPeer(peerId);
        event.setLastReadSeq(row.getLastReadSeq());
        try {
            kafkaTemplate.send(KafkaTopics.MESSAGE_READ, String.valueOf(peerId),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("publish read receipt failed: {}", e.getMessage());
        }
    }

    private static Long toEpochMilli(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
