package com.offerget.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.common.BusinessException;
import com.offerget.dto.auth.AuthResponse;
import com.offerget.dto.auth.LoginRequest;
import com.offerget.dto.auth.RegisterRequest;
import com.offerget.dto.auth.UserVO;
import com.offerget.entity.AppUser;
import com.offerget.mapper.UserMapper;
import com.offerget.security.JwtTokenProvider;
import com.offerget.security.SecurityUtils;
import com.offerget.service.AuthService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("用户名已被占用");
        }

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setGraduationYear(request.graduationYear());
        userMapper.insert(user);

        String token = jwtTokenProvider.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, toVO(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        String token = jwtTokenProvider.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, toVO(user));
    }

    @Override
    public UserVO currentUser() {
        AppUser user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BusinessException.unauthorized("账号不存在或已停用");
        }
        return toVO(user);
    }

    private UserVO toVO(AppUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getAvatar(), user.getGraduationYear());
    }

}
