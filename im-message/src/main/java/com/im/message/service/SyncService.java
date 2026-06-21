package com.im.message.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.message.dto.SyncItem;
import com.im.message.dto.SyncResponse;
import com.im.message.entity.Inbox;
import com.im.message.entity.Message;
import com.im.message.mapper.InboxMapper;
import com.im.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 多端漫游增量同步：按 inbox.user_seq 拉取 > sinceSeq 的消息（升序）。
 */
@Service
@RequiredArgsConstructor
public class SyncService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final InboxMapper inboxMapper;
    private final MessageMapper messageMapper;

    public SyncResponse sync(long userId, long sinceSeq, int limit) {
        int size = (limit <= 0 || limit > MAX_LIMIT) ? DEFAULT_LIMIT : limit;

        List<Inbox> rows = inboxMapper.selectList(
                Wrappers.<Inbox>lambdaQuery()
                        .eq(Inbox::getOwnerId, userId)
                        .gt(Inbox::getUserSeq, sinceSeq)
                        .orderByAsc(Inbox::getUserSeq)
                        .last("limit " + size));

        SyncResponse resp = new SyncResponse();
        if (rows.isEmpty()) {
            resp.setItems(List.of());
            resp.setNextSeq(sinceSeq);
            resp.setHasMore(false);
            return resp;
        }

        List<Long> msgIds = rows.stream().map(Inbox::getMsgId).toList();
        Map<Long, Message> byMsgId = messageMapper.selectList(
                        Wrappers.<Message>lambdaQuery().in(Message::getMsgId, msgIds))
                .stream()
                .collect(Collectors.toMap(Message::getMsgId, Function.identity(), (a, b) -> a));

        List<SyncItem> items = new ArrayList<>(rows.size());
        for (Inbox box : rows) {
            Message m = byMsgId.get(box.getMsgId());
            if (m == null) {
                continue; // 正文缺失(理论上不会发生)，跳过
            }
            SyncItem it = new SyncItem();
            it.setUserSeq(box.getUserSeq());
            it.setConversationId(box.getConversationId());
            it.setMsgId(m.getMsgId());
            it.setSeq(m.getSeq());
            it.setSenderId(m.getSenderId());
            it.setType(m.getType());
            it.setContent(m.getContent());
            it.setCreatedAt(toEpochMilli(m.getCreatedAt()));
            it.setStatus(m.getStatus());
            items.add(it);
        }

        resp.setItems(items);
        resp.setNextSeq(rows.get(rows.size() - 1).getUserSeq());
        resp.setHasMore(rows.size() == size);
        return resp;
    }

    private static Long toEpochMilli(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
