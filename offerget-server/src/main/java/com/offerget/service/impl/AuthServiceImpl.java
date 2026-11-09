package com.offerget.service.impl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.common.BusinessException;
import com.offerget.common.EmailNormalizer;
import com.offerget.dto.auth.AuthResponse;
import com.offerget.dto.auth.EmailCodeLoginRequest;
import com.offerget.dto.auth.LoginRequest;
import com.offerget.dto.auth.RegisterRequest;
import com.offerget.dto.auth.UserVO;
import com.offerget.entity.AppUser;
import com.offerget.mapper.UserMapper;
import com.offerget.security.JwtTokenProvider;
import com.offerget.security.SecurityUtils;
import com.offerget.service.AuthService;
import com.offerget.service.EmailCodeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    private final EmailCodeService emailCodeService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("用户名已被占用");
        }

        String email = EmailNormalizer.normalize(request.email());
        if (email != null && emailExists(email, null)) {
            throw BusinessException.conflict("该邮箱已被其他账号绑定");
        }

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setEmail(email);
        user.setGraduationYear(request.graduationYear());
        try {
            userMapper.insert(user);
        }
        catch (DuplicateKeyException ex) {
            // 并发注册时由数据库唯一索引兜底
            throw BusinessException.conflict("用户名或邮箱已被占用");
        }

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
    public AuthResponse loginByEmailCode(EmailCodeLoginRequest request) {
        emailCodeService.verify(request.email(), request.code());

        String email = EmailNormalizer.normalize(request.email());
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, email));
        if (user == null) {
            throw BusinessException.badRequest("该邮箱未绑定账号，请先注册或在个人中心绑定邮箱");
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

    private boolean emailExists(String email, Long excludeUserId) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, email);
        if (excludeUserId != null) {
            wrapper.ne(AppUser::getId, excludeUserId);
        }
        Long count = userMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private UserVO toVO(AppUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getAvatar(), user.getGraduationYear());
    }

}
