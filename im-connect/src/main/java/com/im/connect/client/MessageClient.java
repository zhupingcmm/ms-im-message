package com.im.connect.client;

import com.im.common.api.Result;
import com.im.common.dto.SendMessageRequest;
import com.im.common.dto.SendMessageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 调用 im-message 发送消息（经 Nacos 服务发现 + 负载均衡）。
 */
@FeignClient(name = "im-message")
public interface MessageClient {

    @PostMapping("/messages/send")
    Result<SendMessageResponse> send(@RequestBody SendMessageRequest req);
}
