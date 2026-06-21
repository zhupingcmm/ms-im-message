package com.im.media.controller;

import com.im.common.api.Result;
import com.im.media.dto.PresignRequest;
import com.im.media.dto.PresignResponse;
import com.im.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒体上传授权。userId 由 im-gateway 经 X-User-Id 注入。
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/presign")
    public Result<PresignResponse> presign(@RequestHeader("X-User-Id") long userId,
                                           @RequestBody(required = false) PresignRequest req) {
        return Result.ok(mediaService.presign(userId, req));
    }
}
