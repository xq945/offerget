package com.offerget.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.common.BusinessException;
import com.offerget.dto.notification.NotificationListVO;
import com.offerget.dto.notification.NotificationVO;
import com.offerget.entity.Job;
import com.offerget.entity.JobEvent;
import com.offerget.entity.NotificationRead;
import com.offerget.enums.EventType;
import com.offerget.enums.JobStatus;
import com.offerget.mapper.JobEventMapper;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.NotificationReadMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * 提醒按天动态生成：面试/笔试临近、岗位截止、逾期未投递。
 * key 内带日期，跨天后会重新变为未读。
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final JobMapper jobMapper;

    private final JobEventMapper jobEventMapper;

    private final NotificationReadMapper readMapper;

    @Override
    public NotificationListVO list() {
        Long userId = SecurityUtils.currentUserId();
        LocalDate today = LocalDate.now();
        List<Generated> generated = generate(userId, today);
        if (generated.isEmpty()) {
            return new NotificationListVO(0, List.of());
        }

        Set<String> readKeys = readMapper.selectList(new LambdaQueryWrapper<NotificationRead>()
                        .eq(NotificationRead::getUserId, userId)
                        .in(NotificationRead::getNotificationKey,
                                generated.stream().map(Generated::key).toList()))
                .stream()
                .map(NotificationRead::getNotificationKey)
                .collect(Collectors.toSet());

        List<NotificationVO> items = generated.stream()
                .map(item -> new NotificationVO(item.key(), item.type(), item.title(),
                        item.description(), item.level(), item.time(), item.jobId(),
                        item.company(), readKeys.contains(item.key())))
                .toList();
        long unread = items.stream().filter(item -> !item.read()).count();
        return new NotificationListVO(unread, items);
    }

    @Override
    @Transactional
    public void markRead(String key) {
        Long userId = SecurityUtils.currentUserId();
        boolean exists = generate(userId, LocalDate.now()).stream()
                .anyMatch(item -> item.key().equals(key));
        if (!exists) {
            throw BusinessException.badRequest("提醒不存在或已失效");
        }
        insertRead(userId, List.of(key));
    }

    @Override
    @Transactional
    public void markAllRead() {
        Long userId = SecurityUtils.currentUserId();
        List<String> keys = generate(userId, LocalDate.now()).stream()
                .map(Generated::key)
                .toList();
        insertRead(userId, keys);
    }

    private List<Generated> generate(Long userId, LocalDate today) {
        List<Job> jobs = jobMapper.selectList(new LambdaQueryWrapper<Job>()
                .eq(Job::getUserId, userId));
        Map<Long, Job> jobMap = jobs.stream()
                .collect(Collectors.toMap(Job::getId, job -> job));

        List<JobEvent> events = jobEventMapper.selectList(new LambdaQueryWrapper<JobEvent>()
                .eq(JobEvent::getUserId, userId)
                .ge(JobEvent::getEventTime, today.atStartOfDay())
                .lt(JobEvent::getEventTime, today.plusDays(3).atStartOfDay())
                .orderByAsc(JobEvent::getEventTime));

        List<Generated> items = new ArrayList<>();

        for (JobEvent event : events) {
            boolean interview = EventType.INTERVIEW.name().equals(event.getEventType());
            boolean writtenTest = EventType.WRITTEN_TEST.name().equals(event.getEventType());
            if (!interview && !writtenTest) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, event.getEventTime().toLocalDate());
            String dayLabel = days == 0 ? "今天" : days == 1 ? "明天" : "后天";
            String typeLabel = interview ? "面试" : "笔试";
            String time = event.getEventTime().format(TIME_FORMAT);
            String description = dayLabel + " " + time
                    + (StringUtils.hasText(event.getNote()) ? " · " + event.getNote() : "");
            String type = interview
                    ? (days == 0 ? "INTERVIEW_TODAY" : "INTERVIEW_SOON")
                    : (days == 0 ? "TEST_TODAY" : "TEST_SOON");
            Job job = jobMap.get(event.getJobId());
            items.add(new Generated(
                    "event:" + event.getId() + ":" + today,
                    type,
                    typeLabel + " · " + event.getTitle(),
                    description,
                    days == 0 ? "HIGH" : "MEDIUM",
                    time,
                    event.getJobId(),
                    job == null ? "" : job.getCompany(),
                    event.getEventTime()));
        }

        for (Job job : jobs) {
            if (JobStatus.OFFER.name().equals(job.getStatus())
                    || JobStatus.REJECTED.name().equals(job.getStatus())
                    || job.getDeadline() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, job.getDeadline());
            String label = job.getCompany() + " · " + job.getTitle();
            if (days < 0 && JobStatus.PENDING.name().equals(job.getStatus())) {
                items.add(new Generated(
                        "overdue:" + job.getId() + ":" + today,
                        "OVERDUE",
                        "「" + label + "」已过截止",
                        "截止 " + job.getDeadline().format(DATE_FORMAT)
                                + "，状态仍为待投递，请更新或删除",
                        "HIGH",
                        job.getDeadline().format(DATE_FORMAT),
                        job.getId(),
                        job.getCompany(),
                        job.getDeadline().atTime(23, 59)));
            }
            else if (days >= 0 && days <= 3) {
                String timeText = days == 0 ? "今天截止" : "还有 " + days + " 天截止";
                items.add(new Generated(
                        "deadline:" + job.getId() + ":" + today,
                        days == 0 ? "DEADLINE_TODAY" : "DEADLINE_SOON",
                        "「" + label + "」" + timeText,
                        "当前状态：" + JobStatus.from(job.getStatus()).label()
                                + " · 截止 " + job.getDeadline().format(DATE_FORMAT),
                        days <= 1 ? "HIGH" : "MEDIUM",
                        job.getDeadline().format(DATE_FORMAT),
                        job.getId(),
                        job.getCompany(),
                        job.getDeadline().atTime(23, 59)));
            }
        }

        items.sort(Comparator
                .comparingInt((Generated item) -> levelWeight(item.level()))
                .thenComparing(Generated::sortTime));
        return items;
    }

    private void insertRead(Long userId, List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        Set<String> existing = readMapper.selectList(new LambdaQueryWrapper<NotificationRead>()
                        .eq(NotificationRead::getUserId, userId)
                        .in(NotificationRead::getNotificationKey, keys))
                .stream()
                .map(NotificationRead::getNotificationKey)
                .collect(Collectors.toSet());

        for (String key : keys) {
            if (existing.contains(key)) {
                continue;
            }
            NotificationRead record = new NotificationRead();
            record.setUserId(userId);
            record.setNotificationKey(key);
            readMapper.insert(record);
        }
    }

    private int levelWeight(String level) {
        return switch (level) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private record Generated(
            String key,
            String type,
            String title,
            String description,
            String level,
            String time,
            Long jobId,
            String company,
            LocalDateTime sortTime) {
    }

}
