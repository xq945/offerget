package com.offerget.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offerget.common.BusinessException;
import com.offerget.common.PageQuery;
import com.offerget.common.PageResult;
import com.offerget.dto.job.JobRequest;
import com.offerget.dto.job.JobVO;
import com.offerget.entity.AiMatchRecord;
import com.offerget.entity.Job;
import com.offerget.entity.JobEvent;
import com.offerget.entity.Resume;
import com.offerget.enums.EventType;
import com.offerget.enums.JobStatus;
import com.offerget.mapper.AiMatchRecordMapper;
import com.offerget.mapper.JobEventMapper;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.ResumeMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.JobService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobMapper jobMapper;

    private final ResumeMapper resumeMapper;

    private final JobEventMapper jobEventMapper;

    private final AiMatchRecordMapper matchRecordMapper;

    @Override
    public PageResult<JobVO> page(PageQuery pageQuery, String status, String keyword) {
        Long userId = SecurityUtils.currentUserId();
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<Job>()
                .eq(Job::getUserId, userId)
                .orderByDesc(Job::getCreatedAt);
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(Job::getStatus, JobStatus.from(status).name());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Job::getCompany, kw).or().like(Job::getTitle, kw));
        }

        Page<Job> page = jobMapper.selectPage(
                new Page<>(pageQuery.safePage(), pageQuery.safeSize()), wrapper);
        Map<Long, String> resumeNames = loadResumeNames(userId);
        List<JobVO> records = page.getRecords().stream()
                .map(job -> toVO(job, resumeNames, false))
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    public JobVO detail(Long id) {
        Job job = requireOwnedJob(id);
        return toVO(job, loadResumeNames(job.getUserId()), true);
    }

    @Override
    @Transactional
    public JobVO create(JobRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Job job = new Job();
        job.setUserId(userId);
        job.setStatus(JobStatus.PENDING.name());
        applyRequest(job, request);

        if (job.getResumeId() == null) {
            Resume defaultResume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                    .eq(Resume::getUserId, userId)
                    .eq(Resume::getIsDefault, true)
                    .last("limit 1"));
            if (defaultResume != null) {
                job.setResumeId(defaultResume.getId());
            }
        }

        jobMapper.insert(job);
        syncDeadlineEvent(job);
        return detail(job.getId());
    }

    @Override
    @Transactional
    public JobVO update(Long id, JobRequest request) {
        Job job = requireOwnedJob(id);
        applyRequest(job, request);
        jobMapper.updateById(job);
        syncDeadlineEvent(job);
        return detail(id);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String status) {
        Job job = requireOwnedJob(id);
        JobStatus newStatus = JobStatus.from(status);
        job.setStatus(newStatus.name());
        jobMapper.updateById(job);

        EventType eventType = mapEventType(newStatus);
        if (eventType != null) {
            JobEvent event = new JobEvent();
            event.setUserId(job.getUserId());
            event.setJobId(job.getId());
            event.setEventType(eventType.name());
            event.setTitle(job.getCompany() + " · " + newStatus.label());
            event.setEventTime(LocalDateTime.now());
            jobEventMapper.insert(event);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Job job = requireOwnedJob(id);
        jobMapper.deleteById(job.getId());
        jobEventMapper.delete(new LambdaQueryWrapper<JobEvent>()
                .eq(JobEvent::getJobId, job.getId())
                .eq(JobEvent::getUserId, job.getUserId()));
        matchRecordMapper.delete(new LambdaQueryWrapper<AiMatchRecord>()
                .eq(AiMatchRecord::getJobId, job.getId())
                .eq(AiMatchRecord::getUserId, job.getUserId()));
    }

    private Job requireOwnedJob(Long id) {
        Job job = jobMapper.selectById(id);
        if (job == null || !job.getUserId().equals(SecurityUtils.currentUserId())) {
            throw BusinessException.notFound("岗位不存在");
        }
        return job;
    }

    private void applyRequest(Job job, JobRequest request) {
        if (request.resumeId() != null) {
            Resume resume = resumeMapper.selectById(request.resumeId());
            if (resume == null || !resume.getUserId().equals(SecurityUtils.currentUserId())) {
                throw BusinessException.badRequest("绑定的简历版本不存在");
            }
        }
        job.setCompany(request.company().trim());
        job.setTitle(request.title().trim());
        job.setCity(trimToNull(request.city()));
        job.setSalaryRange(trimToNull(request.salaryRange()));
        job.setJobType(trimToNull(request.jobType()));
        job.setSource(trimToNull(request.source()));
        job.setJdText(request.jdText());
        job.setDeadline(request.deadline());
        job.setResumeId(request.resumeId());
        job.setNote(trimToNull(request.note()));
    }

    /** 岗位截止日期变更时，同步维护一条 DEADLINE 事件，供台历与待办使用 */
    private void syncDeadlineEvent(Job job) {
        jobEventMapper.delete(new LambdaQueryWrapper<JobEvent>()
                .eq(JobEvent::getJobId, job.getId())
                .eq(JobEvent::getEventType, EventType.DEADLINE.name()));
        if (job.getDeadline() == null) {
            return;
        }
        JobEvent event = new JobEvent();
        event.setUserId(job.getUserId());
        event.setJobId(job.getId());
        event.setEventType(EventType.DEADLINE.name());
        event.setTitle(job.getCompany() + "投递截止");
        event.setEventTime(job.getDeadline().atTime(23, 59));
        jobEventMapper.insert(event);
    }

    private EventType mapEventType(JobStatus status) {
        return switch (status) {
            case APPLIED -> EventType.APPLY;
            case WRITTEN_TEST -> EventType.WRITTEN_TEST;
            case INTERVIEW -> EventType.INTERVIEW;
            case OFFER, REJECTED -> EventType.RESULT;
            case PENDING -> null;
        };
    }

    private Map<Long, String> loadResumeNames(Long userId) {
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(Resume::getId, Resume::getName));
    }

    private JobVO toVO(Job job, Map<Long, String> resumeNames, boolean withJd) {
        Long daysLeft = job.getDeadline() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), job.getDeadline());
        String resumeName = job.getResumeId() == null ? null : resumeNames.get(job.getResumeId());
        return new JobVO(job.getId(), job.getCompany(), job.getTitle(), job.getCity(),
                job.getSalaryRange(), job.getJobType(), job.getSource(),
                withJd ? job.getJdText() : null, job.getDeadline(), job.getStatus(),
                job.getMatchScore(), job.getResumeId(), resumeName, job.getNote(),
                daysLeft, job.getCreatedAt(), job.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
