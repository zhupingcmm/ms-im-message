package com.im.message.controller;

import com.im.common.api.Result;
import com.im.message.dto.SyncResponse;
import com.im.message.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多端漫游增量同步。userId 由 im-gateway 经 X-User-Id 注入。
 */
@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    /** 拉取 user_seq > sinceSeq 的增量消息（升序）。 */
    @GetMapping
    public Result<SyncResponse> sync(@RequestHeader("X-User-Id") long userId,
                                     @RequestParam(value = "sinceSeq", defaultValue = "0") long sinceSeq,
                                     @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return Result.ok(syncService.sync(userId, sinceSeq, limit));
    }
}
