package com.im.user.dto;

import lombok.Data;

/**
 * 用户公开信息（供会话列表/消息展示解析昵称、头像）。
 */
@Data
public class UserBrief {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
}
