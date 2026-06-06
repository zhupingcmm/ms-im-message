package com.im.common.util;

/**
 * 会话 ID 生成。
 */
public final class ConversationIdUtil {

    private ConversationIdUtil() {
    }

    /** 单聊会话 ID：与双方顺序无关，s_{小}_{大}。 */
    public static String single(long a, long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return "s_" + lo + "_" + hi;
    }
}
