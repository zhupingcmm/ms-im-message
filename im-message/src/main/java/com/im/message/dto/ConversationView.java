package com.im.message.dto;

import lombok.Data;

/**
 * 会话列表项（返回给前端）。对端昵称/头像由前端用 peerId 调 im-user 批量解析。
 */
@Data
public class ConversationView {
    private Long peerId;
    private String conversationId;
    private Long lastMsgId;
    private Long lastSeq;
    private String lastContent;
    /** 最后一条消息时间（epoch 毫秒）。 */
    private Long lastMsgTime;
    private Integer unreadCount;
}
