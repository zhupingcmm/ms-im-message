package com.im.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送消息响应（im-message -> im-connect）。
 */
@Data
public class SendMessageResponse implements Serializable {

    private String clientMsgId;
    private Long msgId;
    private Long seq;
    private String conversationId;
    private Long createdAt;
}
