package com.im.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.message.entity.Conversation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 收件箱 upsert：首次插入，已存在则刷新最后一条消息并累加未读。
     * 发送方一侧 unreadInc=0；接收方一侧 unreadInc=1。
     */
    @Insert("INSERT INTO conversation " +
            "(owner_id, peer_id, conversation_id, last_msg_id, last_seq, last_content, last_msg_time, unread_count) " +
            "VALUES (#{ownerId}, #{peerId}, #{conversationId}, #{lastMsgId}, #{lastSeq}, #{lastContent}, #{lastMsgTime}, #{unreadInc}) " +
            "ON DUPLICATE KEY UPDATE " +
            "last_msg_id = VALUES(last_msg_id), " +
            "last_seq = VALUES(last_seq), " +
            "last_content = VALUES(last_content), " +
            "last_msg_time = VALUES(last_msg_time), " +
            "unread_count = unread_count + #{unreadInc}")
    int upsert(@Param("ownerId") long ownerId,
               @Param("peerId") long peerId,
               @Param("conversationId") String conversationId,
               @Param("lastMsgId") long lastMsgId,
               @Param("lastSeq") long lastSeq,
               @Param("lastContent") String lastContent,
               @Param("lastMsgTime") LocalDateTime lastMsgTime,
               @Param("unreadInc") int unreadInc);

    /** 进入会话后清零未读，并把已读位点推进到当前最后一条。 */
    @Update("UPDATE conversation SET unread_count = 0, last_read_seq = last_seq " +
            "WHERE owner_id = #{ownerId} AND peer_id = #{peerId}")
    int markRead(@Param("ownerId") long ownerId, @Param("peerId") long peerId);
}
