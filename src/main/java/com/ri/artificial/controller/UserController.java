package com.ri.artificial.controller;

import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.dto.LoginFormDTO;
import com.ri.artificial.domain.vo.UserLoginVO;
import com.ri.artificial.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author Ri
 * @date 2026-10-01 16:29
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final IUserService userService;

    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody @Validated LoginFormDTO loginFormDTO) {
        return userService.login(loginFormDTO);
    }

    @GetMapping("/logout")
    public Result<String> logout(Long userId) {
        return userService.logout(userId);
    }
}
