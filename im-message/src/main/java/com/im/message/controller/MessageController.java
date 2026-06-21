package com.im.message.controller;

import com.im.common.api.Result;
import com.im.common.dto.SendMessageRequest;
import com.im.common.dto.SendMessageResponse;
import com.im.message.dto.HistoryResponse;
import com.im.message.service.ConversationService;
import com.im.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    @PostMapping("/send")
    public Result<SendMessageResponse> send(@RequestBody SendMessageRequest req) {
        return Result.ok(messageService.send(req));
    }

    /**
     * 拉取 userId 与 peerId 的历史消息（游标分页）。
     * beforeSeq 省略取最新一页；翻旧消息时传上一页返回的 nextCursor。
     */
    @GetMapping("/history")
    public Result<HistoryResponse> history(@RequestHeader("X-User-Id") long userId,
                                           @RequestParam("peerId") long peerId,
                                           @RequestParam(value = "beforeSeq", required = false) Long beforeSeq,
                                           @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.ok(conversationService.history(userId, peerId, beforeSeq, limit));
    }
}
