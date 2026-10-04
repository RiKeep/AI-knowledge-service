package com.ri.artificial.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.dto.LoginFormDTO;
import com.ri.artificial.domain.po.User;
import com.ri.artificial.domain.vo.UserLoginVO;

/**
 * @author Ri
 * @date 2026-10-01 19:47
 */
public interface IUserService extends IService<User> {
    Result<UserLoginVO> login(LoginFormDTO loginFormDTO);

    Result<String> logout(Long userId);
}
