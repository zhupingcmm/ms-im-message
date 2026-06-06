package com.im.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送消息请求（im-connect -> im-message）。
 */
@Data
public class SendMessageRequest implements Serializable {

    /** 客户端生成的幂等 ID。 */
    private String clientMsgId;
    /** 发送者 userId（由网关从连接绑定写入，客户端不可伪造）。 */
    private Long from;
    /** 接收者 userId（单聊）。 */
    private Long to;
    /** 消息类型：1=text（P0 仅文本）。 */
    private Integer type;
    /** 文本内容。 */
    private String content;
}
