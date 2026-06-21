package com.im.message.dto;

import lombok.Data;

/**
 * 历史消息项（返回给前端）。
 */
@Data
public class MessageView {
    private Long msgId;
    private Long seq;
    private Long senderId;
    private Integer type;
    private String content;
    /** 创建时间（epoch 毫秒）。 */
    private Long createdAt;
    /** 1=normal 2=recalled。 */
    private Integer status;
}
