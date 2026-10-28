package com.offerget.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.common.BusinessException;
import com.offerget.dto.profile.ChangePasswordRequest;
import com.offerget.dto.profile.ProfileVO;
import com.offerget.dto.profile.UpdateProfileRequest;
import com.offerget.entity.AiMatchRecord;
import com.offerget.entity.AppUser;
import com.offerget.entity.Job;
import com.offerget.entity.Resume;
import com.offerget.entity.ResumeOptimization;
import com.offerget.mapper.AiMatchRecordMapper;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.ResumeMapper;
import com.offerget.mapper.ResumeOptimizationMapper;
import com.offerget.mapper.UserMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.ProfileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;

    private final JobMapper jobMapper;

    private final ResumeMapper resumeMapper;

    private final AiMatchRecordMapper matchRecordMapper;

    private final ResumeOptimizationMapper optimizationMapper;

    private final PasswordEncoder passwordEncoder;

    @Override
    public ProfileVO detail() {
        AppUser user = requireUser();
        return toVO(user);
    }

    @Override
    @Transactional
    public ProfileVO update(UpdateProfileRequest request) {
        AppUser user = requireUser();
        user.setNickname(request.nickname().trim());
        user.setEmail(StringUtils.hasText(request.email()) ? request.email().trim() : null);
        user.setGraduationYear(request.graduationYear());
        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AppUser user = requireUser();
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("原密码不正确");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw BusinessException.badRequest("新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    private AppUser requireUser() {
        AppUser user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BusinessException.unauthorized("账号不存在或已停用");
        }
        return user;
    }

    private ProfileVO toVO(AppUser user) {
        Long userId = user.getId();
        long jobCount = jobMapper.selectCount(new LambdaQueryWrapper<Job>().eq(Job::getUserId, userId));
        long resumeCount = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
        long matchCount = matchRecordMapper.selectCount(new LambdaQueryWrapper<AiMatchRecord>()
                .eq(AiMatchRecord::getUserId, userId));
        long optimizationCount = optimizationMapper.selectCount(new LambdaQueryWrapper<ResumeOptimization>()
                .eq(ResumeOptimization::getUserId, userId));
        long days = user.getCreatedAt() == null ? 0
                : ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now()) + 1;

        ProfileVO.Stats stats = new ProfileVO.Stats(jobCount, resumeCount, matchCount,
                optimizationCount, Math.max(days, 1));
        return new ProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getGraduationYear(), user.getCreatedAt(), stats);
    }

}
