package com.im.message.dto;

import lombok.Data;

import java.util.List;

/**
 * 历史消息分页响应。messages 按 seq 升序（旧->新）。
 * 继续向上翻页时用 nextCursor 作为下次请求的 beforeSeq。
 */
@Data
public class HistoryResponse {
    private String conversationId;
    private List<MessageView> messages;
    /** 本页最旧一条的 seq；为 null 表示无更多。 */
    private Long nextCursor;
    private boolean hasMore;
}
