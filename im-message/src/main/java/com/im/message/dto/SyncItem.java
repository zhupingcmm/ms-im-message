package com.im.message.dto;

import lombok.Data;

/**
 * 漫游同步项：在消息正文之上带 userSeq（客户端据此推进本地锚点）。
 */
@Data
public class SyncItem {
    /** 用户级序号（漫游锚点）。 */
    private Long userSeq;
    private String conversationId;
    private Long msgId;
    /** 会话内序号。 */
    private Long seq;
    private Long senderId;
    private Integer type;
    private String content;
    private Long createdAt;
    private Integer status;
}
