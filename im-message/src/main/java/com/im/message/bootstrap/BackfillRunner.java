package com.im.message.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.constant.RedisKeys;
import com.im.message.entity.Inbox;
import com.im.message.entity.Message;
import com.im.message.mapper.ConversationMapper;
import com.im.message.mapper.InboxMapper;
import com.im.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量回填（D4）：把改动前已有的 message 回填进 inbox + conversation，并把 Redis user_seq 计数器
 * 抬到回填后的最大值，使后续 INCR 不与回填值冲突。
 *
 * 幂等：inbox 已有数据则整体跳过（只在首次写扩散落地时执行一次）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackfillRunner implements ApplicationRunner {

    private final MessageMapper messageMapper;
    private final InboxMapper inboxMapper;
    private final ConversationMapper conversationMapper;
    private final StringRedisTemplate redis;

    @Override
    public void run(ApplicationArguments args) {
        Long inboxCount = inboxMapper.selectCount(null);
        if (inboxCount != null && inboxCount > 0) {
            return; // 已回填过，跳过
        }

        // 按时间、msgId 升序处理，使会话摘要的"最后一条"以最新者收尾
        List<Message> messages = messageMapper.selectList(
                Wrappers.<Message>lambdaQuery()
                        .orderByAsc(Message::getCreatedAt)
                        .orderByAsc(Message::getMsgId));
        if (messages.isEmpty()) {
            return;
        }

        Map<Long, Long> userSeqCounter = new HashMap<>();   // ownerId -> 已分配的最大 user_seq
        int inboxRows = 0;
        for (Message m : messages) {
            long sender = m.getSenderId();
            Long peer = peerOf(m.getConversationId(), sender);
            if (peer == null) {
                continue; // 非单聊会话ID，跳过
            }
            String preview = m.getContent() == null ? ""
                    : (m.getContent().length() > 255 ? m.getContent().substring(0, 255) : m.getContent());

            inboxMapper.insert(buildInbox(sender, nextSeq(userSeqCounter, sender), m));
            inboxMapper.insert(buildInbox(peer, nextSeq(userSeqCounter, peer), m));
            inboxRows += 2;

            // 回填会话摘要：unreadInc=0（历史视为已读），保留可能已存在的未读
            conversationMapper.upsert(sender, peer, m.getConversationId(), m.getMsgId(), m.getSeq(), preview, m.getCreatedAt(), 0);
            conversationMapper.upsert(peer, sender, m.getConversationId(), m.getMsgId(), m.getSeq(), preview, m.getCreatedAt(), 0);
        }

        // 抬高 Redis user_seq 计数器到回填后的最大值
        userSeqCounter.forEach((owner, maxSeq) -> seedAtLeast(RedisKeys.userSeq(owner), maxSeq));

        log.info("backfill done: {} messages -> {} inbox rows, seeded {} user_seq counters",
                messages.size(), inboxRows, userSeqCounter.size());
    }

    private static long nextSeq(Map<Long, Long> counter, long owner) {
        long next = counter.getOrDefault(owner, 0L) + 1;
        counter.put(owner, next);
        return next;
    }

    private static Inbox buildInbox(long ownerId, long userSeq, Message m) {
        Inbox box = new Inbox();
        box.setOwnerId(ownerId);
        box.setUserSeq(userSeq);
        box.setConversationId(m.getConversationId());
        box.setMsgId(m.getMsgId());
        box.setConvSeq(m.getSeq());
        box.setCreatedAt(m.getCreatedAt());
        return box;
    }

    /** 从单聊会话ID s_{lo}_{hi} 解析出 self 的对端；非法格式返回 null。 */
    private static Long peerOf(String conversationId, long self) {
        if (conversationId == null || !conversationId.startsWith("s_")) {
            return null;
        }
        String[] parts = conversationId.split("_");
        if (parts.length != 3) {
            return null;
        }
        try {
            long lo = Long.parseLong(parts[1]);
            long hi = Long.parseLong(parts[2]);
            return self == lo ? hi : lo;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 仅当 Redis 现值小于 target 时抬高。 */
    private void seedAtLeast(String key, long target) {
        String cur = redis.opsForValue().get(key);
        long curVal = 0L;
        if (cur != null) {
            try {
                curVal = Long.parseLong(cur);
            } catch (NumberFormatException ignore) {
                // 非法值直接覆盖
            }
        }
        if (target > curVal) {
            redis.opsForValue().set(key, String.valueOf(target));
        }
    }
}
