package com.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收件箱行（写扩散）。
 */
@Data
@TableName("inbox")
public class Inbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    /** 用户级递增序号（漫游锚点）。 */
    private Long userSeq;
    private String conversationId;
    /** 指向 message.msg_id。 */
    private Long msgId;
    /** 冗余会话内序号。 */
    private Long convSeq;
    private LocalDateTime createdAt;
}
