package com.ri.artificial.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpStatus;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.dto.LoginFormDTO;
import com.ri.artificial.domain.po.User;
import com.ri.artificial.domain.vo.UserLoginVO;
import com.ri.artificial.mapper.UserMapper;
import com.ri.artificial.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * @author Ri
 * @date 2026-10-01 19:49
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public Result<UserLoginVO> login(LoginFormDTO loginFormDTO) {
        User user = query().eq("username", loginFormDTO.getUsername()).one();
        UserLoginVO userLoginVO = new UserLoginVO();
        if (user != null && encoder.matches(loginFormDTO.getPassword(), user.getPassword())) {
            // 如果用户存在且密码匹配，生成token并返回
            StpUtil.login(user.getId());
            userLoginVO.setUserId(user.getId())
                    .setToken(StpUtil.getTokenValue())
                    .setExpire(StpUtil.getTokenTimeout());
            return Result.success(userLoginVO);
        }
        return Result.error(HttpStatus.HTTP_UNAUTHORIZED, "用户名或密码错误");
    }

    @Override
    public Result<String> logout(Long userId) {
        // 如果用户未登录，直接抛出 NotLoginException 异常，全局捕获
        StpUtil.checkLogin();
        // 如果用户已登录，执行注销操作
        StpUtil.logout(userId);
        return Result.success();
    }
}
