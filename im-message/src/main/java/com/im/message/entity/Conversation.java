package com.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话（每用户收件箱）。一条单聊消息维护双方各一行。
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话归属用户（收件箱所有者）。 */
    private Long ownerId;
    /** 对端用户。 */
    private Long peerId;
    /** 会话ID s_{小}_{大}。 */
    private String conversationId;
    private Long lastMsgId;
    private Long lastSeq;
    /** 最后一条消息预览（截断）。 */
    private String lastContent;
    private LocalDateTime lastMsgTime;
    private Integer unreadCount;
    /** 已读到的会话内序号。 */
    private Long lastReadSeq;
    private LocalDateTime updatedAt;
}
