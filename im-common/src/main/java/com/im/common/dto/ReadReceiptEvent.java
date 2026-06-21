package com.im.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 已读回执事件（Kafka 载荷）：reader 已读到 conversationId 的 lastReadSeq，
 * im-connect 消费后把回执推给对端发送方 peer。
 */
@Data
public class ReadReceiptEvent implements Serializable {

    private String conversationId;
    /** 上报已读的用户。 */
    private Long reader;
    /** 接收回执通知的对端（reader 的会话对端）。 */
    private Long peer;
    /** 已读到的会话内序号。 */
    private Long lastReadSeq;
}
