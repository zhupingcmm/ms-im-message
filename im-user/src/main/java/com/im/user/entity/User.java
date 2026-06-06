package com.im.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    /** 密码哈希（BCrypt）。 */
    private String password;
    private Integer status;
    private LocalDateTime createdAt;
}
