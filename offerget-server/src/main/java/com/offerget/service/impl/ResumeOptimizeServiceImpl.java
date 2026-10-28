package com.offerget.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.common.BusinessException;
import com.offerget.dto.ai.OptimizeRequest;
import com.offerget.dto.ai.OptimizeResultVO;
import com.offerget.entity.Job;
import com.offerget.entity.Resume;
import com.offerget.entity.ResumeOptimization;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.ResumeMapper;
import com.offerget.mapper.ResumeOptimizationMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.ResumeOptimizeService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ResumeOptimizeServiceImpl implements ResumeOptimizeService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final TypeReference<List<OptimizeResultVO.OptimizeSection>> SECTION_LIST = new TypeReference<>() {
    };

    private final ResumeOptimizeEngine optimizeEngine;

    private final JobMapper jobMapper;

    private final ResumeMapper resumeMapper;

    private final ResumeOptimizationMapper optimizationMapper;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public OptimizeResultVO optimize(OptimizeRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Job job = jobMapper.selectById(request.jobId());
        if (job == null || !job.getUserId().equals(userId)) {
            throw BusinessException.notFound("岗位不存在");
        }
        Resume resume = resumeMapper.selectById(request.resumeId());
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.notFound("简历不存在");
        }

        ResumeOptimizeEngine.Report report = optimizeEngine.optimize(job.getJdText(), resume.getContentText());
        List<OptimizeResultVO.OptimizeSection> sections = report.sections().stream()
                .map(section -> new OptimizeResultVO.OptimizeSection(
                        section.title(), section.original(), section.suggestion(), section.keywords()))
                .toList();

        ResumeOptimization record = new ResumeOptimization();
        record.setUserId(userId);
        record.setJobId(job.getId());
        record.setResumeId(resume.getId());
        record.setScoreBefore(report.scoreBefore());
        record.setScoreAfter(report.scoreAfter());
        record.setAddedKeywords(writeJson(report.addedKeywords()));
        record.setSections(writeJson(sections));
        record.setSummary(report.summary());
        optimizationMapper.insert(record);

        ResumeOptimization saved = optimizationMapper.selectById(record.getId());
        return toVO(saved, job, resume);
    }

    @Override
    public List<OptimizeResultVO> history(Long jobId) {
        Long userId = SecurityUtils.currentUserId();
        List<ResumeOptimization> records = optimizationMapper.selectList(
                new LambdaQueryWrapper<ResumeOptimization>()
                        .eq(ResumeOptimization::getUserId, userId)
                        .eq(jobId != null, ResumeOptimization::getJobId, jobId)
                        .orderByDesc(ResumeOptimization::getCreatedAt)
                        .last("limit 20"));
        if (records.isEmpty()) {
            return List.of();
        }

        Set<Long> jobIds = records.stream().map(ResumeOptimization::getJobId).collect(Collectors.toSet());
        Set<Long> resumeIds = records.stream().map(ResumeOptimization::getResumeId).collect(Collectors.toSet());
        Map<Long, Job> jobMap = jobMapper.selectBatchIds(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, job -> job));
        Map<Long, Resume> resumeMap = resumeMapper.selectBatchIds(resumeIds).stream()
                .collect(Collectors.toMap(Resume::getId, resume -> resume));

        return records.stream()
                .map(record -> toVO(record, jobMap.get(record.getJobId()), resumeMap.get(record.getResumeId())))
                .toList();
    }

    private OptimizeResultVO toVO(ResumeOptimization record, Job job, Resume resume) {
        return new OptimizeResultVO(
                record.getId(),
                record.getJobId(),
                job == null ? null : job.getTitle(),
                job == null ? null : job.getCompany(),
                record.getResumeId(),
                resume == null ? null : resume.getName(),
                record.getScoreBefore(),
                record.getScoreAfter(),
                readJson(record.getAddedKeywords(), STRING_LIST, List.of()),
                readJson(record.getSections(), SECTION_LIST, List.of()),
                record.getSummary(),
                record.getCreatedAt());
    }

    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        }
        catch (Exception ex) {
            return fallback;
        }
    }

}
