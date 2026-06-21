package com.im.common.constant;

/**
 * Kafka topic 约定。
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** 消息下行投递：im-message 生产，im-connect 消费后推给在线接收方。 */
    public static final String MESSAGE_DOWNLINK = "im.message.downlink";

    /** 离线待推：im-message 生产，im-push 消费（P1 骨架，未接真实通道）。 */
    public static final String MESSAGE_OFFLINE = "im.message.offline";

    /** 已读回执：im-message 生产，im-connect 消费后反向通知发送方。 */
    public static final String MESSAGE_READ = "im.message.read";
}
