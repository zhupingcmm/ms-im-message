package com.im.common.constant;

/**
 * Redis key 约定。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 连接路由：route:{userId} -> Set(gatewayId)。 */
    public static String route(long userId) {
        return "route:" + userId;
    }

    /** 会话内序号：seq:conv:{conversationId} (INCR)。 */
    public static String convSeq(String conversationId) {
        return "seq:conv:" + conversationId;
    }

    /** 用户级序号：seq:user:{userId} (INCR)，多端漫游锚点。 */
    public static String userSeq(long userId) {
        return "seq:user:" + userId;
    }

    /** 发送幂等：idemp:{clientMsgId} -> "msgId:seq"。 */
    public static String idemp(String clientMsgId) {
        return "idemp:" + clientMsgId;
    }
}
