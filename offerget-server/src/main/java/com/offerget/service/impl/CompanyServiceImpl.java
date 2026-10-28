package com.offerget.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offerget.common.BusinessException;
import com.offerget.common.PageQuery;
import com.offerget.common.PageResult;
import com.offerget.dto.company.CompanyVO;
import com.offerget.dto.job.JobRequest;
import com.offerget.dto.job.JobVO;
import com.offerget.entity.CompanyCalendar;
import com.offerget.entity.Job;
import com.offerget.mapper.CompanyCalendarMapper;
import com.offerget.mapper.JobMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.CompanyService;
import com.offerget.service.JobService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyCalendarMapper companyMapper;

    private final JobMapper jobMapper;

    private final JobService jobService;

    @Override
    public PageResult<CompanyVO> page(PageQuery pageQuery, String keyword, String industry, String city) {
        Long userId = SecurityUtils.currentUserId();
        LambdaQueryWrapper<CompanyCalendar> wrapper = new LambdaQueryWrapper<CompanyCalendar>()
                .orderByAsc(CompanyCalendar::getDeadline);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(CompanyCalendar::getCompany, kw)
                    .or().like(CompanyCalendar::getJobTitle, kw));
        }
        if (StringUtils.hasText(industry)) {
            wrapper.eq(CompanyCalendar::getIndustry, industry.trim());
        }
        if (StringUtils.hasText(city)) {
            wrapper.eq(CompanyCalendar::getCity, city.trim());
        }

        Page<CompanyCalendar> page = companyMapper.selectPage(
                new Page<>(pageQuery.safePage(), pageQuery.safeSize()), wrapper);

        Set<String> importedKeys = jobMapper.selectList(new LambdaQueryWrapper<Job>()
                        .eq(Job::getUserId, userId))
                .stream()
                .map(job -> job.getCompany() + "|" + job.getTitle())
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now();
        List<CompanyVO> records = page.getRecords().stream()
                .map(item -> toVO(item, today, importedKeys))
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    @Transactional
    public JobVO importToJobs(Long id) {
        Long userId = SecurityUtils.currentUserId();
        CompanyCalendar company = companyMapper.selectById(id);
        if (company == null) {
            throw BusinessException.notFound("校招信息不存在");
        }
        if ("PROJECT".equals(company.getInfoType())) {
            throw BusinessException.badRequest("招聘项目条目不能直接加入投递，请选择具体岗位");
        }

        Long exists = jobMapper.selectCount(new LambdaQueryWrapper<Job>()
                .eq(Job::getUserId, userId)
                .eq(Job::getCompany, company.getCompany())
                .eq(Job::getTitle, company.getJobTitle()));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("该岗位已在你的投递列表中");
        }

        String note = StringUtils.hasText(company.getApplyUrl())
                ? "来自校招信息库 · 投递入口：" + company.getApplyUrl()
                : "来自校招信息库";
        JobRequest request = new JobRequest(
                company.getCompany(),
                company.getJobTitle(),
                company.getCity(),
                null,
                "校招",
                "校招信息库",
                company.getDescription(),
                company.getDeadline(),
                null,
                note);
        return jobService.create(request);
    }

    private CompanyVO toVO(CompanyCalendar company, LocalDate today, Set<String> importedKeys) {
        Long daysLeft = company.getDeadline() == null ? null
                : ChronoUnit.DAYS.between(today, company.getDeadline());
        boolean expired = company.getDeadline() != null && company.getDeadline().isBefore(today);
        boolean imported = importedKeys.contains(company.getCompany() + "|" + company.getJobTitle());
        List<String> tags = StringUtils.hasText(company.getTags())
                ? List.of(company.getTags().split(","))
                : List.of();
        return new CompanyVO(company.getId(), company.getCompany(), company.getIndustry(), company.getCity(),
                company.getJobTitle(), company.getBatch(), company.getInfoType(),
                company.getOpenDate(), company.getDeadline(),
                company.getApplyUrl(), company.getSourceUrl(),
                tags, company.getDescription(), company.getTone(),
                daysLeft, expired, imported);
    }

}
