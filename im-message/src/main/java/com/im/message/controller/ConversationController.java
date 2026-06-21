package com.im.message.controller;

import com.im.common.api.Result;
import com.im.message.dto.ConversationView;
import com.im.message.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话列表 / 未读相关接口。
 * userId 由 im-gateway 校验 JWT 后通过 X-User-Id 头注入，不再信任客户端入参（D1）。
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 登录后拉会话列表（按最后消息时间倒序）。 */
    @GetMapping
    public Result<List<ConversationView>> list(@RequestHeader("X-User-Id") long userId) {
        return Result.ok(conversationService.listConversations(userId));
    }

    /** 进入与 peer 的会话后上报已读位点（清零未读 + 记录 last_read_seq + 触发回执）。 */
    @PostMapping("/read")
    public Result<Void> read(@RequestHeader("X-User-Id") long userId,
                             @RequestParam("peerId") long peerId) {
        conversationService.markRead(userId, peerId);
        return Result.ok();
    }
}
