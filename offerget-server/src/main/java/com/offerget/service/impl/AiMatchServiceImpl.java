package com.offerget.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.offerget.common.BusinessException;
import com.offerget.dto.ai.MatchRequest;
import com.offerget.dto.ai.MatchResultVO;
import com.offerget.entity.AiMatchRecord;
import com.offerget.entity.Job;
import com.offerget.entity.Resume;
import com.offerget.mapper.AiMatchRecordMapper;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.ResumeMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.AiMatchService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AiMatchServiceImpl implements AiMatchService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AiMatchRecordMapper matchRecordMapper;

    private final JobMapper jobMapper;

    private final ResumeMapper resumeMapper;

    private final AiMatchEngine matchEngine;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public MatchResultVO match(MatchRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Job job = jobMapper.selectById(request.jobId());
        if (job == null || !job.getUserId().equals(userId)) {
            throw BusinessException.notFound("岗位不存在");
        }
        Resume resume = resumeMapper.selectById(request.resumeId());
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.notFound("简历不存在");
        }

        AiMatchEngine.MatchReport report = matchEngine.analyze(job.getJdText(), resume.getContentText());

        AiMatchRecord record = new AiMatchRecord();
        record.setUserId(userId);
        record.setJobId(job.getId());
        record.setResumeId(resume.getId());
        record.setScore(report.score());
        record.setMatchedKeywords(writeJson(report.matched()));
        record.setMissingKeywords(writeJson(report.missing().stream()
                .map(AiMatchEngine.Missing::keyword).toList()));
        record.setSuggestions(writeJson(report.suggestions()));
        record.setSummary(report.summary());
        matchRecordMapper.insert(record);

        jobMapper.update(null, new LambdaUpdateWrapper<Job>()
                .eq(Job::getId, job.getId())
                .set(Job::getMatchScore, report.score()));

        return toVO(record, job, resume);
    }

    @Override
    public List<MatchResultVO> history(Long jobId) {
        Long userId = SecurityUtils.currentUserId();
        List<AiMatchRecord> records = matchRecordMapper.selectList(
                new LambdaQueryWrapper<AiMatchRecord>()
                        .eq(AiMatchRecord::getUserId, userId)
                        .eq(jobId != null, AiMatchRecord::getJobId, jobId)
                        .orderByDesc(AiMatchRecord::getCreatedAt)
                        .last("limit 20"));
        if (records.isEmpty()) {
            return List.of();
        }

        Job job = jobMapper.selectById(records.get(0).getJobId());
        Resume resume = resumeMapper.selectById(records.get(0).getResumeId());
        return records.stream().map(record -> toVO(record, job, resume)).toList();
    }

    @Override
    public MatchResultVO latest(Long jobId) {
        Long userId = SecurityUtils.currentUserId();
        AiMatchRecord record = matchRecordMapper.selectOne(
                new LambdaQueryWrapper<AiMatchRecord>()
                        .eq(AiMatchRecord::getUserId, userId)
                        .eq(AiMatchRecord::getJobId, jobId)
                        .orderByDesc(AiMatchRecord::getCreatedAt)
                        .last("limit 1"));
        if (record == null) {
            return null;
        }
        Job job = jobMapper.selectById(record.getJobId());
        Resume resume = resumeMapper.selectById(record.getResumeId());
        return toVO(record, job, resume);
    }

    private MatchResultVO toVO(AiMatchRecord record, Job job, Resume resume) {
        return new MatchResultVO(
                record.getId(),
                record.getJobId(),
                job == null ? null : job.getTitle(),
                job == null ? null : job.getCompany(),
                record.getResumeId(),
                resume == null ? null : resume.getName(),
                record.getScore(),
                readJson(record.getMatchedKeywords()),
                readJson(record.getMissingKeywords()),
                readJson(record.getSuggestions()),
                record.getSummary(),
                record.getCreatedAt());
    }

    private String writeJson(List<String> values) {
        return objectMapper.writeValueAsString(values);
    }

    private List<String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        }
        catch (Exception ex) {
            return List.of();
        }
    }

}
