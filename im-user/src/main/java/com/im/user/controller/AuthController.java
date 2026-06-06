package com.im.user.controller;

import com.im.common.api.Result;
import com.im.user.dto.LoginRequest;
import com.im.user.dto.LoginResponse;
import com.im.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        try {
            return Result.ok(userService.login(req.getUsername(), req.getPassword()));
        } catch (IllegalArgumentException e) {
            return Result.fail(40001, e.getMessage());
        }
    }
}
