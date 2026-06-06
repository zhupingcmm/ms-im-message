package com.im.message.controller;

import com.im.common.api.Result;
import com.im.common.dto.SendMessageRequest;
import com.im.common.dto.SendMessageResponse;
import com.im.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/send")
    public Result<SendMessageResponse> send(@RequestBody SendMessageRequest req) {
        return Result.ok(messageService.send(req));
    }
}
