package com.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话ID（单聊 s_{小}_{大}）。分片键（P0 单库）。 */
    private String conversationId;
    /** 全局消息ID（雪花）。 */
    private Long msgId;
    /** 会话内递增序号 conv_seq。 */
    private Long seq;
    private Long senderId;
    /** 1=text。 */
    private Integer type;
    private String content;
    private String mediaUrl;
    /** 1=normal 2=recalled。 */
    private Integer status;
    private LocalDateTime createdAt;
}
