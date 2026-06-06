package com.im.common.constant;

/**
 * Kafka topic 约定。
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** 消息下行投递：im-message 生产，im-connect 消费后推给在线接收方。 */
    public static final String MESSAGE_DOWNLINK = "im.message.downlink";
}
