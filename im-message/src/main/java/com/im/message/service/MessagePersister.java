package com.im.message.service;

import com.im.common.constant.RedisKeys;
import com.im.message.entity.Inbox;
import com.im.message.entity.Message;
import com.im.message.mapper.ConversationMapper;
import com.im.message.mapper.InboxMapper;
import com.im.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消息落库 + 写扩散的事务单元（同库本地事务）。
 * 单独成 Bean 以确保 @Transactional 代理生效，且 Kafka 发布留在事务外（见 MessageService）。
 */
@Component
@RequiredArgsConstructor
public class MessagePersister {

    private final MessageMapper messageMapper;
    private final InboxMapper inboxMapper;
    private final ConversationMapper conversationMapper;
    private final StringRedisTemplate redis;

    /**
     * 一个事务内：写 message 正文 + 收发双方各一行 inbox（带各自 user_seq）+ 维护双方会话摘要。
     */
    @Transactional
    public void persist(Message msg, long from, long to, String conversationId, String preview, LocalDateTime now) {
        messageMapper.insert(msg);

        long fromUserSeq = redis.opsForValue().increment(RedisKeys.userSeq(from));
        long toUserSeq = redis.opsForValue().increment(RedisKeys.userSeq(to));
        inboxMapper.insert(buildInbox(from, fromUserSeq, conversationId, msg.getMsgId(), msg.getSeq(), now));
        inboxMapper.insert(buildInbox(to, toUserSeq, conversationId, msg.getMsgId(), msg.getSeq(), now));

        // 发送方一侧不计未读；接收方一侧未读 +1
        conversationMapper.upsert(from, to, conversationId, msg.getMsgId(), msg.getSeq(), preview, now, 0);
        conversationMapper.upsert(to, from, conversationId, msg.getMsgId(), msg.getSeq(), preview, now, 1);
    }

    private static Inbox buildInbox(long ownerId, long userSeq, String conversationId,
                                    long msgId, long convSeq, LocalDateTime now) {
        Inbox box = new Inbox();
        box.setOwnerId(ownerId);
        box.setUserSeq(userSeq);
        box.setConversationId(conversationId);
        box.setMsgId(msgId);
        box.setConvSeq(convSeq);
        box.setCreatedAt(now);
        return box;
    }
}
