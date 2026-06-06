package com.im.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 下行消息事件（Kafka 载荷）：im-message 生产，im-connect 消费后推送在线接收方。
 */
@Data
public class MessageEvent implements Serializable {

    private Long msgId;
    private Long seq;
    private String conversationId;
    private Long from;
    private Long to;
    private Integer type;
    private String content;
    private Long createdAt;
}
