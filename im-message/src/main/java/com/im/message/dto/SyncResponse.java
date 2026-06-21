package com.im.message.dto;

import lombok.Data;

import java.util.List;

/**
 * 漫游增量同步响应。items 按 userSeq 升序。
 * 客户端把 nextSeq 作为下次请求的 sinceSeq 继续拉，直到 hasMore=false。
 */
@Data
public class SyncResponse {
    private List<SyncItem> items;
    /** 本页最大 userSeq；客户端据此推进锚点。 */
    private Long nextSeq;
    private boolean hasMore;
}
